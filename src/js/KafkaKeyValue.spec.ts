import KafkaKeyValue, { streamResponseBody, compressGzipPayload, decompressGzipResponse, LAST_SEEN_OFFSETS_HEADER_NAME, KKV_FETCH_RETRY_OPTIONS, NotFoundError, UpdateRequestBody } from './KafkaKeyValue';
import updateEvents from './update-events';
import { EventEmitter } from 'events';
import { fail } from 'assert';
import { LabelValues } from 'prom-client';
import { RequestInit } from 'node-fetch';

const promClientMock = {
  Counter: class Counter {
    inc: any
    dec: any
    labels: any
    reset: any
    remove: any

    constructor(options) {

      this.inc = jest.fn();
      this.dec = jest.fn();
      this.labels = jest.fn().mockReturnValue(this);
      this.reset = jest.fn();
      this.remove = jest.fn();
    }
  },
  Gauge: class Gauge {
    inc: any
    dec: any
    set: any
    labels: any
    reset: any
    setToCurrentTime: any
    startTimer: any
    remove: any

    constructor(options) {

      this.inc = jest.fn();
      this.dec = jest.fn();
      this.set = jest.fn();
      this.labels = jest.fn().mockReturnValue(this);
      this.reset = jest.fn();
      this.setToCurrentTime = jest.fn();
      this.startTimer = jest.fn().mockReturnValue(() => jest.fn());
      this.remove = jest.fn();
    }
  },

  Histogram: class Histogram {
    observe: any
    startTimer: any
    labels: any
    reset: any
    remove: any

    constructor(options) {

      this.observe = jest.fn();
      this.startTimer = jest.fn().mockReturnValue(() => jest.fn());
      this.labels = jest.fn().mockReturnValue(this);
      this.reset = jest.fn();
      this.remove = jest.fn();
    }

    zero(labels: LabelValues<string>): void {
      throw new Error('Not implemented in mock');
    }
  },
};

describe('KafkaKeyValue', function () {

  it('can be told to abort slow get requests', async function () {
    const fetchMock = jest.fn();

    const metrics = KafkaKeyValue.createMetrics(promClientMock.Counter, promClientMock.Gauge, promClientMock.Histogram);
    const kkv = new KafkaKeyValue({
      cacheHost: 'http://cache-kkv',
      metrics,
      pixyHost: 'http://pixy',
      topicName: 'testtopic01',
      fetchImpl: fetchMock,
    });

    const successGetResponse = {
      status: 200,
      ok: true,
      json: async () => ({ myvalue: true }),
      headers: new Map([
        [LAST_SEEN_OFFSETS_HEADER_NAME, JSON.stringify([])]
      ])
    };

    const respondSlowlyButSuccessfullyIfAllowed = (url: string, options: RequestInit) => {
      return new Promise((resolve, reject) => {
        options?.signal?.addEventListener('abort', () => {
          reject(new Error('MOCK_ABORTED_REQUEST'));
          clearTimeout(timer);
        });
        const timer = setTimeout(() => {
          resolve(successGetResponse);
        }, timeoutMs * 2);
      })
    };

    const timeoutMs = 100;

    fetchMock.mockImplementationOnce(respondSlowlyButSuccessfullyIfAllowed);
    fetchMock.mockImplementationOnce(respondSlowlyButSuccessfullyIfAllowed);
    fetchMock.mockImplementationOnce(respondSlowlyButSuccessfullyIfAllowed);
    fetchMock.mockImplementationOnce(respondSlowlyButSuccessfullyIfAllowed);
    fetchMock.mockImplementationOnce(respondSlowlyButSuccessfullyIfAllowed);
    fetchMock.mockImplementationOnce(respondSlowlyButSuccessfullyIfAllowed);

    await expect(kkv.get('k1', { abortRequestsAfterMs: timeoutMs })).rejects.toEqual(new Error('MOCK_ABORTED_REQUEST'));

    expect(fetchMock).toHaveBeenCalledTimes(6);

    fetchMock.mockImplementationOnce(respondSlowlyButSuccessfullyIfAllowed);
    fetchMock.mockImplementationOnce(respondSlowlyButSuccessfullyIfAllowed);
    fetchMock.mockResolvedValueOnce(successGetResponse);

    await expect(kkv.get('k1', { abortRequestsAfterMs: timeoutMs })).resolves.toEqual({ myvalue: true });

    expect(fetchMock).toHaveBeenCalledTimes(6 + 3);
  });

  it('get does not retry on 404s by default', async function () {
    const fetchMock = jest.fn();

    const metrics = KafkaKeyValue.createMetrics(promClientMock.Counter, promClientMock.Gauge, promClientMock.Histogram);
    const kkv = new KafkaKeyValue({
      cacheHost: 'http://cache-kkv',
      metrics,
      pixyHost: 'http://pixy',
      topicName: 'testtopic01',
      fetchImpl: fetchMock,
    });

    const missingGetResponse = {
      status: 404,
      ok: true,
      json: async () => ({}),
      headers: new Map([])
    };

    const successGetResponse = {
      status: 200,
      ok: true,
      json: async () => ({}),
      headers: new Map([
        [LAST_SEEN_OFFSETS_HEADER_NAME, JSON.stringify([])]
      ])
    };

    fetchMock.mockResolvedValueOnce(missingGetResponse);
    fetchMock.mockResolvedValueOnce(successGetResponse);

    await expect(kkv.get('k1')).rejects.toEqual(new NotFoundError('Cache does not contain key: k1'));
  });

  describe('retries from gets triggered by onupdate as a way to handle scaled kkv deployments where replicas will some times be behind each other', function () {

    it('does not retry forever', async function () {
      const fetchMock = jest.fn();

      const metrics = KafkaKeyValue.createMetrics(promClientMock.Counter, promClientMock.Gauge, promClientMock.Histogram);
      const kkv = new KafkaKeyValue({
        cacheHost: 'http://cache-kkv',
        metrics,
        pixyHost: 'http://pixy',
        topicName: 'testtopic05',
        fetchImpl: fetchMock,
      });

      const missingGetResponse = {
        status: 404,
        ok: true,
        headers: new Map([])
      };

      const update: UpdateRequestBody = {
        offsets: {
          '0': 3
        },
        topic: 'testtopic05',
        updates: {
          'key1': {}
        },
        v: 1
      };

      const onUpdateSpy = jest.fn();

      kkv.onUpdate(onUpdateSpy);

      fetchMock.mockResolvedValueOnce(missingGetResponse);
      fetchMock.mockResolvedValueOnce(missingGetResponse);
      fetchMock.mockResolvedValueOnce(missingGetResponse);
      fetchMock.mockResolvedValueOnce(missingGetResponse);
      fetchMock.mockResolvedValueOnce(missingGetResponse);
      fetchMock.mockResolvedValueOnce(missingGetResponse);

      await expect(kkv.updateListener(update)).rejects.toEqual(new Error('Cache does not contain key: key1'));

      expect(fetchMock.mock.calls.map(args => [args[0]])).toEqual([
        ['http://cache-kkv/cache/v1/raw/key1'],
        ['http://cache-kkv/cache/v1/raw/key1'],
        ['http://cache-kkv/cache/v1/raw/key1'],
        ['http://cache-kkv/cache/v1/raw/key1'],
        ['http://cache-kkv/cache/v1/raw/key1'],
        ['http://cache-kkv/cache/v1/raw/key1'],
      ]);

      expect(onUpdateSpy.mock.calls).toEqual([]);
    });

    it('retries on 404', async function () {
      const fetchMock = jest.fn();

      const metrics = KafkaKeyValue.createMetrics(promClientMock.Counter, promClientMock.Gauge, promClientMock.Histogram);
      const kkv = new KafkaKeyValue({
        cacheHost: 'http://cache-kkv',
        metrics,
        pixyHost: 'http://pixy',
        topicName: 'testtopic02',
        fetchImpl: fetchMock,
      });

      const missingGetResponse = {
        status: 404,
        ok: true,
        headers: new Map([])
      };

      const successGetResponse = {
        status: 200,
        ok: true,
        json: async () => ({ myValue: true }),
        headers: new Map([
          [LAST_SEEN_OFFSETS_HEADER_NAME, JSON.stringify([
            { topic: 'testtopic02', partition: 0, offset: 3 }
          ])]
        ])
      };

      const update: UpdateRequestBody = {
        offsets: {
          '0': 3
        },
        topic: 'testtopic02',
        updates: {
          'key1': {}
        },
        v: 1
      };

      const onUpdateSpy = jest.fn();

      kkv.onUpdate(onUpdateSpy);

      fetchMock.mockResolvedValueOnce(missingGetResponse);
      fetchMock.mockResolvedValueOnce(missingGetResponse);
      fetchMock.mockResolvedValueOnce(missingGetResponse);
      fetchMock.mockResolvedValueOnce(missingGetResponse);
      fetchMock.mockResolvedValueOnce(missingGetResponse);
      fetchMock.mockResolvedValueOnce(successGetResponse);

      // @ts-expect-error
      jest.spyOn(kkv.logger, 'warn');

      updateEvents.emit('update', update);
      await new Promise(resolve => setTimeout(resolve, 10));

      expect(fetchMock.mock.calls.map(args => [args[0]])).toEqual([
        ['http://cache-kkv/cache/v1/raw/key1'],
        ['http://cache-kkv/cache/v1/raw/key1'],
        ['http://cache-kkv/cache/v1/raw/key1'],
        ['http://cache-kkv/cache/v1/raw/key1'],
        ['http://cache-kkv/cache/v1/raw/key1'],
        ['http://cache-kkv/cache/v1/raw/key1'],
      ]);

      expect(onUpdateSpy.mock.calls).toEqual([
        ['key1', { myValue: true }]
      ]);

      // @ts-expect-error
      expect(kkv.logger.warn).toHaveBeenCalledTimes(5);
    });

    it('retries if the offset requirement is not satisfied', async function () {
      const fetchMock = jest.fn();

      const metrics = KafkaKeyValue.createMetrics(promClientMock.Counter, promClientMock.Gauge, promClientMock.Histogram);
      const kkv = new KafkaKeyValue({
        cacheHost: 'http://cache-kkv',
        metrics,
        pixyHost: 'http://pixy',
        topicName: 'testtopic03',
        fetchImpl: fetchMock,
      });

      const missingGetResponse = {
        status: 404,
        ok: true,
        headers: new Map([])
      };

      const outdatedGetResponse = {
        status: 200,
        ok: true,
        json: async () => ({}),
        headers: new Map([
          [LAST_SEEN_OFFSETS_HEADER_NAME, JSON.stringify([
            { topic: 'testtopic03', partition: 0, offset: 3 },
            { topic: 'testtopic03', partition: 1, offset: 2 },
            { topic: 'testtopic03', partition: 2, offset: 1 },
          ])]
        ])
      };

      const successGetResponse = {
        status: 200,
        ok: true,
        json: async () => ({ myValue: true }),
        headers: new Map([
          [LAST_SEEN_OFFSETS_HEADER_NAME, JSON.stringify([
            { topic: 'testtopic03', partition: 0, offset: 4 }
          ])]
        ])
      };

      const update: UpdateRequestBody = {
        offsets: {
          '0': 4
        },
        topic: 'testtopic03',
        updates: {
          'key1': {}
        },
        v: 1
      };

      const onUpdateSpy = jest.fn();

      kkv.onUpdate(onUpdateSpy);

      fetchMock.mockResolvedValueOnce(missingGetResponse);
      fetchMock.mockResolvedValueOnce(outdatedGetResponse);
      fetchMock.mockResolvedValueOnce(outdatedGetResponse);
      fetchMock.mockResolvedValueOnce(outdatedGetResponse);
      fetchMock.mockResolvedValueOnce(outdatedGetResponse);
      fetchMock.mockResolvedValueOnce(successGetResponse);

      updateEvents.emit('update', update);
      await new Promise(resolve => setTimeout(resolve, 10));

      expect(fetchMock.mock.calls.map(args => [args[0]])).toEqual([
        ['http://cache-kkv/cache/v1/raw/key1'],
        ['http://cache-kkv/cache/v1/raw/key1'],
        ['http://cache-kkv/cache/v1/raw/key1'],
        ['http://cache-kkv/cache/v1/raw/key1'],
        ['http://cache-kkv/cache/v1/raw/key1'],
        ['http://cache-kkv/cache/v1/raw/key1'],
      ]);

      expect(onUpdateSpy.mock.calls).toEqual([
        ['key1', { myValue: true }]
      ]);
    });
  });

  describe('retries as a way to avoid ECONNREFUSED and ETIMEDOUT errors when kkv pods are terminating', function () {

    it('works for get requests', async function () {
     
      const fetchMock = jest.fn();

      const metrics = KafkaKeyValue.createMetrics(promClientMock.Counter, promClientMock.Gauge, promClientMock.Histogram);
      const kkv = new KafkaKeyValue({
        cacheHost: 'http://cache-kkv',
        metrics,
        pixyHost: 'http://pixy',
        topicName: 'testtopic01',
        fetchImpl: fetchMock,
      });

      const successGetResponse = {
        status: 200,
        ok: true,
        json: async () => ({ offset: 3 }),
        headers: new Map([
          [LAST_SEEN_OFFSETS_HEADER_NAME, JSON.stringify([])]
        ])
      };

      fetchMock.mockRejectedValueOnce(new Error('MOCKED_ETIMEDOUT'));
      fetchMock.mockRejectedValueOnce(new Error('MOCKED_SOMETHINGELSE'));
      fetchMock.mockResolvedValueOnce(successGetResponse);

      await kkv.get('k1');
    });

    it('works for values stream', async function () {
      const fetchMock = jest.fn();

      const metrics = KafkaKeyValue.createMetrics(promClientMock.Counter, promClientMock.Gauge, promClientMock.Histogram);
      const kkv = new KafkaKeyValue({
        cacheHost: 'http://cache-kkv',
        metrics,
        pixyHost: 'http://pixy',
        topicName: 'testtopic01',
        fetchImpl: fetchMock,
      });

      const bodyStream = new EventEmitter();

      const successValuesResponse = {
        status: 200,
        ok: true,
        body: bodyStream,
        headers: new Map([
          [LAST_SEEN_OFFSETS_HEADER_NAME, JSON.stringify([])]
        ])
      };

      fetchMock.mockRejectedValueOnce(new Error('MOCKED_ETIMEDOUT'));
      fetchMock.mockRejectedValueOnce(new Error('MOCKED_SOMETHINGELSE'));
      fetchMock.mockResolvedValueOnce(successValuesResponse);

      const streamCompleted = kkv.streamValues(() => { });

      // We have to wait for the retry attempts to finish before we end the stream
      await new Promise(resolve => setTimeout(resolve, KKV_FETCH_RETRY_OPTIONS.intervalMs * 2 + 30));
      bodyStream.emit('end');

      await streamCompleted;
    });
  });

  describe('Sending put requests reliably to pixy', function () {

    it('needs to retry for a while before failing', async function () {

      const failedResponse = {
        status: 503,
        json: () => {}
      };

      const successResponse = {
        status: 200,
        json: async () => ({ offset: 3 })
      };

      const fetchMock = jest.fn();
      fetchMock.mockResolvedValueOnce(failedResponse);
      fetchMock.mockResolvedValueOnce(successResponse);

      const metrics = KafkaKeyValue.createMetrics(promClientMock.Counter, promClientMock.Gauge, promClientMock.Histogram);
      const kkv = new KafkaKeyValue({
        cacheHost: 'http://cache-kkv',
        metrics,
        pixyHost: 'http://pixy',
        topicName: 'testtopic01',
        fetchImpl: fetchMock,
      });

      const offset = await kkv.put('key1', 'value1');
      expect(fetchMock).toHaveBeenCalledTimes(2);
      expect(offset).toEqual(3);
    });

    it('rejects after a number of times', async function () {

      const failedResponse = {
        status: 503,
        json: () => {}
      };

      const fetchMock = jest.fn();
      fetchMock.mockResolvedValue(failedResponse);

      const metrics = KafkaKeyValue.createMetrics(promClientMock.Counter, promClientMock.Gauge, promClientMock.Histogram);
      const kkv = new KafkaKeyValue({
        cacheHost: 'http://cache-kkv',
        metrics,
        pixyHost: 'http://pixy',
        topicName: 'testtopic01',
        fetchImpl: fetchMock,
      });

      try {
        await kkv.put('key1', 'value1', { intervalMs: 100, nRetries: 10 });
        fail('Put should have rejected eventually if we never get 200 back');
      } catch (err) {
        expect(fetchMock).toHaveBeenCalledTimes(11);
      }
    });
  });

  describe('gzipping payloads pre-put', function () {

    it('works', async function () {
      const buffer: Buffer = await compressGzipPayload(JSON.stringify({ foo: 'bar' }));
      const response = await decompressGzipResponse(console, buffer);
      expect(response).toEqual({ foo: 'bar' });
    });
  });

  describe('streaming values', function () {

    it('works on an empty topic', async function () {
      const bodyStream = new EventEmitter();


      const onValue = jest.fn();
      const streamCompleted = streamResponseBody(console, <any>bodyStream, onValue);

      bodyStream.emit('end');

      await streamCompleted;
      expect(onValue).toHaveBeenCalledTimes(0);
    });

    it('was tricky apparently', async function () {
      const bodyStream = new EventEmitter();


      const onValue = jest.fn();
      const streamCompleted = streamResponseBody(console, <any>bodyStream, onValue);

      bodyStream.emit('data', JSON.stringify({ foo: 'bar' }) + '\n');
      bodyStream.emit('data', JSON.stringify({ foo: 'bar2' }).substr(0, 4));
      bodyStream.emit('data', JSON.stringify({ foo: 'bar2' }).substr(4, 11) + '\n');
      bodyStream.emit('end');

      await streamCompleted;
      expect(onValue).toHaveBeenCalledTimes(2);
      expect(onValue).toBeCalledWith({ foo: 'bar' })
      expect(onValue).toBeCalledWith({ foo: 'bar2' })
    });

    it('updates last seen offset metric based on header value', async function () {
      const response = {
        body: new EventEmitter(),
        headers: new Map([
          ['x-kkv-last-seen-offsets', JSON.stringify([
            { topic: 'testtopic01', partition: 0, offset: 17 }
          ])]
        ])
      };

      const fetchMock = jest.fn().mockReturnValueOnce(response);

      const metrics = KafkaKeyValue.createMetrics(promClientMock.Counter, promClientMock.Gauge, promClientMock.Histogram);
      const kkv = new KafkaKeyValue({
        cacheHost: 'http://cache-kkv',
        metrics,
        pixyHost: 'http://pixy',
        topicName: 'testtopic01',
        fetchImpl: fetchMock
      });

      const streaming = kkv.streamValues(() => {});
      await Promise.resolve();
      await Promise.resolve();
      response.body.emit('end');

      await streaming;

      expect(metrics.kafka_key_value_last_seen_offset.set).toHaveBeenCalledWith(
        {
          topic: 'testtopic01',
          partition: 0
        },
        17
      )
    });
  });

  describe('onupdate handlers', function () {

    it('requires us to document the behavior of a certain payload', async function () {

      const metrics = KafkaKeyValue.createMetrics(promClientMock.Counter, promClientMock.Gauge, promClientMock.Histogram);
      const kkv = new KafkaKeyValue({
        cacheHost: 'http://cache-kkv',
        metrics,
        pixyHost: 'http://pixy',
        topicName: 'testtopic01',
      });

      const onUpdateSpy = jest.fn();
      kkv.onUpdate(onUpdateSpy);
      kkv.get = jest.fn();
      (<jest.Mock>(kkv.get)).mockResolvedValueOnce({ foo: 'bar' })

      updateEvents.emit('update', {
        v: 1,
        topic: 'testtopic01',
        offsets: {
          '0': 28262
        },
        updates: {
          'bd3f6188-d865-443d-8646-03e8f1c643cb': {}
        }
      });

      // Promises needs to resolve before we get new value
      await new Promise(resolve => setTimeout(resolve, 10));

      expect(onUpdateSpy).toHaveBeenCalledTimes(1);
      expect(onUpdateSpy).toHaveBeenCalledWith('bd3f6188-d865-443d-8646-03e8f1c643cb', { foo: 'bar' });

      expect(metrics.kafka_key_value_last_seen_offset.set).toHaveBeenCalledTimes(1);
      expect(metrics.kafka_key_value_last_seen_offset.labels).toHaveBeenCalledTimes(1);
      expect(metrics.kafka_key_value_last_seen_offset.labels).toHaveBeenCalledWith('cache-kkv', 'testtopic01', '0');
      expect(metrics.kafka_key_value_last_seen_offset.set).toHaveBeenCalledWith(28262);

      updateEvents.emit('update', {
        v: 1,
        topic: 'testtopic01',
        offsets: {
          '0': 28263
        },
        updates: {
          'bd3f6188-d865-443d-8646-03e8f1c643cb': {}
        }
      });

      // Promises needs to resolve before we get new value
      await new Promise(resolve => setTimeout(resolve, 10));

      expect(onUpdateSpy).toHaveBeenCalledTimes(2);
    });

    it('only handles updates for the same key once if called within the debounce timeout period', async function () {
      const metrics = KafkaKeyValue.createMetrics(promClientMock.Counter, promClientMock.Gauge, promClientMock.Histogram);
      const kkv = new KafkaKeyValue({
        cacheHost: 'http://cache-kkv',
        metrics,
        pixyHost: 'http://pixy',
        topicName: 'testtopic01',
      });

      const onUpdateSpy = jest.fn();
      kkv.onUpdate(onUpdateSpy);
      kkv.get = jest.fn();
      (<jest.Mock>(kkv.get)).mockResolvedValue({ foo: 'bar' })

      // Three duplicates
      updateEvents.emit('update', {
        v: 1,
        topic: 'testtopic01',
        offsets: {
          '0': 28262
        },
        updates: {
          'bd3f6188-d865-443d-8646-03e8f1c643cb': {}
        }
      });
      updateEvents.emit('update', {
        v: 1,
        topic: 'testtopic01',
        offsets: {
          '0': 28262
        },
        updates: {
          'bd3f6188-d865-443d-8646-03e8f1c643cb': {}
        }
      });
      updateEvents.emit('update', {
        v: 1,
        topic: 'testtopic01',
        offsets: {
          '0': 28262
        },
        updates: {
          'bd3f6188-d865-443d-8646-03e8f1c643cb': {}
        }
      });

      // Three more duplicates with another key
      updateEvents.emit('update', {
        v: 1,
        topic: 'testtopic01',
        offsets: {
          '0': 28262
        },
        updates: {
          'aaaa6188-d865-443d-8646-03e8f1c643cb': {}
        }
      });
      updateEvents.emit('update', {
        v: 1,
        topic: 'testtopic01',
        offsets: {
          '0': 28262
        },
        updates: {
          'aaaa6188-d865-443d-8646-03e8f1c643cb': {}
        }
      });
      updateEvents.emit('update', {
        v: 1,
        topic: 'testtopic01',
        offsets: {
          '0': 28262
        },
        updates: {
          'aaaa6188-d865-443d-8646-03e8f1c643cb': {}
        }
      });

      // Wait a few milliseconds more than the debounce timeout
      await new Promise(resolve => setTimeout(resolve, 20));
      expect(onUpdateSpy).toHaveBeenCalledTimes(2);
      expect(onUpdateSpy).toHaveBeenCalledWith('bd3f6188-d865-443d-8646-03e8f1c643cb', { foo: 'bar' })
      expect(onUpdateSpy).toHaveBeenCalledWith('aaaa6188-d865-443d-8646-03e8f1c643cb', { foo: 'bar' })

      updateEvents.emit('update', {
        v: 1,
        topic: 'testtopic01',
        offsets: {
          '0': 28265
        },
        updates: {
          'aaaa6188-d865-443d-8646-03e8f1c643cb': {}
        }
      });

      await Promise.resolve();

      expect(onUpdateSpy).toHaveBeenCalledTimes(3);
    });
  });

  describe('updatePartitionOffsetMetrics', function () {
    it('only updates metrics with higher offsets, so that debounced onupdate handlers does not reduce the offests', function () {
      const metrics = KafkaKeyValue.createMetrics(promClientMock.Counter, promClientMock.Gauge, promClientMock.Histogram);
      const kkv = new KafkaKeyValue({
        cacheHost: 'http://cache-kkv',
        metrics,
        pixyHost: 'http://pixy',
        topicName: 'testtopic01',
      });

      kkv.updatePartitionOffsetMetrics({
        ['p2']: 2,
        ['p1']: 1,
      });
      expect(metrics.kafka_key_value_last_seen_offset.set).toHaveBeenCalledTimes(2);
      expect(metrics.kafka_key_value_last_seen_offset.set).toHaveBeenCalledWith(1);
      expect(metrics.kafka_key_value_last_seen_offset.set).toHaveBeenCalledWith(2);
      expect(metrics.kafka_key_value_last_seen_offset.labels).toHaveBeenCalledTimes(2);
      expect(metrics.kafka_key_value_last_seen_offset.labels).toHaveBeenCalledWith('cache-kkv', 'testtopic01', 'p1');
      expect(metrics.kafka_key_value_last_seen_offset.labels).toHaveBeenCalledWith('cache-kkv', 'testtopic01', 'p2');

      kkv.updatePartitionOffsetMetrics({
        ['p2']: 1,
        ['p1']: 1,
      });
      expect(metrics.kafka_key_value_last_seen_offset.set).toHaveBeenCalledTimes(2);
      expect(metrics.kafka_key_value_last_seen_offset.labels).toHaveBeenCalledTimes(2);

      kkv.updatePartitionOffsetMetrics({
        ['p2']: 3,
      });
      expect(metrics.kafka_key_value_last_seen_offset.set).toHaveBeenCalledTimes(3);
      expect(metrics.kafka_key_value_last_seen_offset.set).toHaveBeenLastCalledWith(3);
      expect(metrics.kafka_key_value_last_seen_offset.labels).toHaveBeenCalledTimes(3);
      expect(metrics.kafka_key_value_last_seen_offset.labels).toHaveBeenLastCalledWith('cache-kkv', 'testtopic01', 'p2');

    });
  })
});