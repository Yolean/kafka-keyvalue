import KafkaKeyValue, {
  streamResponseBody,
  compressGzipPayload,
  decompressGzipResponse,
  LAST_SEEN_OFFSETS_HEADER_NAME,
  KKV_FETCH_RETRY_OPTIONS,
  NotFoundError,
  UpdateRequestBody,
  KafkaKeyValueWithProducer,
  ProducerFunction,
  IKafkaKeyValue
} from './KafkaKeyValue';
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

      // @ts-expect-error
      const errorSpy = jest.spyOn(kkv.logger, 'error');

      // The listener is bound to an EventEmitter, so a rejection would be an
      // unhandled rejection in the consumer process (live-v3 exited on exactly that,
      // 2026-09-10); it resolves and logs instead.
      await expect(kkv.updateListener(update)).resolves.toBeUndefined();

      expect(fetchMock.mock.calls.map(args => [args[0]])).toEqual([
        ['http://cache-kkv/cache/v1/raw/key1'],
        ['http://cache-kkv/cache/v1/raw/key1'],
        ['http://cache-kkv/cache/v1/raw/key1'],
        ['http://cache-kkv/cache/v1/raw/key1'],
        ['http://cache-kkv/cache/v1/raw/key1'],
        ['http://cache-kkv/cache/v1/raw/key1'],
      ]);

      expect(onUpdateSpy.mock.calls).toEqual([]);
      expect(errorSpy).toHaveBeenCalledTimes(1);
      expect(errorSpy.mock.calls[0][0]).toMatchObject({ key: 'key1', offset: 3, err: new Error('Cache does not contain key: key1') });

      // The same offset pushed again (the other kkv replica) fetches the key again
      fetchMock.mockResolvedValueOnce({
        status: 200, ok: true, json: async () => ({ myValue: true }),
        headers: new Map([[LAST_SEEN_OFFSETS_HEADER_NAME, JSON.stringify([{ topic: 'testtopic05', partition: 0, offset: 3 }])]])
      });
      await kkv.updateListener(update);
      expect(onUpdateSpy.mock.calls).toEqual([['key1', { myValue: true }]]);
    });

    it('a throwing update handler is logged and does not reject the listener', async function () {
      const fetchMock = jest.fn();
      const metrics = KafkaKeyValue.createMetrics(promClientMock.Counter, promClientMock.Gauge, promClientMock.Histogram);
      const kkv = new KafkaKeyValue({
        cacheHost: 'http://cache-kkv',
        metrics,
        topicName: 'testtopic06',
        fetchImpl: fetchMock,
      });
      const first = jest.fn(() => { throw new Error('handler bug'); });
      const second = jest.fn();
      kkv.onUpdate(first);
      kkv.onUpdate(second);
      fetchMock.mockResolvedValue({
        status: 200, ok: true, json: async () => ({ myValue: true }),
        headers: new Map([[LAST_SEEN_OFFSETS_HEADER_NAME, JSON.stringify([{ topic: 'testtopic06', partition: 0, offset: 3 }])]])
      });
      // @ts-expect-error
      const errorSpy = jest.spyOn(kkv.logger, 'error');

      await expect(kkv.updateListener({ v: 1, topic: 'testtopic06', offsets: { '0': 3 }, updates: { key1: {}, key2: {} } })).resolves.toBeUndefined();

      expect(first).toHaveBeenCalledTimes(2);
      expect(errorSpy).toHaveBeenCalledTimes(2);
    });

    it('an unknown protocol version is logged, not thrown', async function () {
      const metrics = KafkaKeyValue.createMetrics(promClientMock.Counter, promClientMock.Gauge, promClientMock.Histogram);
      const kkv = new KafkaKeyValue({ cacheHost: 'http://cache-kkv', metrics, topicName: 'testtopic07', fetchImpl: jest.fn() });
      // @ts-expect-error
      const errorSpy = jest.spyOn(kkv.logger, 'error');
      await expect(kkv.updateListener({ v: 2, topic: 'testtopic07', offsets: {}, updates: {} })).resolves.toBeUndefined();
      expect(errorSpy).toHaveBeenCalledTimes(1);
    });

    it('retries on 404', async function () {
      const fetchMock = jest.fn();

      const metrics = KafkaKeyValue.createMetrics(promClientMock.Counter, promClientMock.Gauge, promClientMock.Histogram);
      const kkv = new KafkaKeyValue({
        cacheHost: 'http://cache-kkv',
        metrics,
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
      const kkv = KafkaKeyValueWithProducer.withPixyProducer({
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
      const kkv = KafkaKeyValueWithProducer.withPixyProducer({
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

  describe('Sending put requests with any producer', function () {
    it('works', async function () {
      const mockProducer = jest.fn();
      mockProducer.mockResolvedValue(0);

      const metrics = KafkaKeyValue.createMetrics(promClientMock.Counter, promClientMock.Gauge, promClientMock.Histogram);
      const kkv = new KafkaKeyValueWithProducer({
        cacheHost: 'http://cache-kkv',
        metrics,
        topicName: 'testtopic01',
        fetchImpl: jest.fn(),
        producer: mockProducer,
      });

      await kkv.put('key1', 'value1');
      expect(mockProducer.mock.calls.map(v => {
        let args = v[0];
        delete args.logger;
        delete args.fetchImpl;
        return args;
      })).toEqual([
        {
          key: 'key1',
          value: '"value1"',
          topic: 'testtopic01',
        },
      ]);

      await kkv.putOther('othertopic', 'key2', 'value2');
      expect(mockProducer.mock.calls.map(v => {
        let args = v[0];
        delete args.logger;
        delete args.fetchImpl;
        return args;
      })).toEqual([
        {
          key: 'key1',
          value: '"value1"',
          topic: 'testtopic01',
        },
        {
          key: 'key2',
          value: '"value2"',
          topic: 'othertopic',
        },
      ]);

      const mockProducer2 = jest.fn();
      mockProducer2.mockResolvedValue(0);

      await kkv.putWithProducer(mockProducer2, 'key3', 'value3');
      await kkv.putOtherWithProducer(mockProducer2, 'othertopic', 'key4', 'value4');
      expect(mockProducer.mock.calls.length).toEqual(2);
      expect(mockProducer2.mock.calls.map(v => {
        let args = v[0];
        delete args.logger;
        delete args.fetchImpl;
        return args;
      })).toEqual([
        {
          key: 'key3',
          value: '"value3"',
          topic: 'testtopic01',
        },
        {
          key: 'key4',
          value: '"value4"',
          topic: 'othertopic',
        },
      ]);
    });

    it('defaults to non-gzipped payloads unless specified in .putOther... args', async function () {
      const mockProducer = jest.fn();
      mockProducer.mockResolvedValue(0);
      const metrics = KafkaKeyValue.createMetrics(promClientMock.Counter, promClientMock.Gauge, promClientMock.Histogram);
      const kkvWithoutGzip = new KafkaKeyValueWithProducer({
        cacheHost: 'http://cache-kkv',
        metrics,
        topicName: 'testtopic01',
        fetchImpl: jest.fn(),
        producer: mockProducer,
      });

      await kkvWithoutGzip.put('key', 'value1');
      await kkvWithoutGzip.putWithProducer(mockProducer, 'key', 'value2');
      await kkvWithoutGzip.putOther('testtopic01', 'key', 'value3');
      await kkvWithoutGzip.putOther('testtopic01', 'key', 'value4', true);
      await kkvWithoutGzip.putOtherWithProducer(mockProducer, 'testtopic01', 'key', 'value5');
      await kkvWithoutGzip.putOtherWithProducer(mockProducer, 'testtopic01', 'key', 'value6', true);
      expect(mockProducer.mock.calls.map(v => {
        let args = v[0];
        delete args.logger;
        delete args.fetchImpl;
        return args;
      })).toEqual([
        {
          key: 'key',
          value: '"value1"',
          topic: 'testtopic01',
        },
        {
          key: 'key',
          value: '"value2"',
          topic: 'testtopic01',
        },
        {
          key: 'key',
          value: '"value3"',
          topic: 'testtopic01',
        },
        {
          key: 'key',
          value: await compressGzipPayload('"value4"'),
          topic: 'testtopic01',
        },
        {
          key: 'key',
          value: '"value5"',
          topic: 'testtopic01',
        },
        {
          key: 'key',
          value: await compressGzipPayload('"value6"'),
          topic: 'testtopic01',
        },
      ]);
    });

    it('defaults to gzipped payloads if specified in constructor', async function () {
      const mockProducer = jest.fn();
      mockProducer.mockResolvedValue(0);
      const kkvWithGzip = new KafkaKeyValueWithProducer({
        cacheHost: 'http://cache-kkv',
        metrics: KafkaKeyValue.createMetrics(promClientMock.Counter, promClientMock.Gauge, promClientMock.Histogram),
        topicName: 'testtopic01',
        fetchImpl: jest.fn(),
        producer: mockProducer,
        gzip: true,
      });

      await kkvWithGzip.put('key', 'value1');
      await kkvWithGzip.putWithProducer(mockProducer, 'key', 'value2');
      // ...Other defaults to no gzip since we cannot know if the specified topic is gzipped
      await kkvWithGzip.putOther('testtopic01', 'key', 'value3');
      await kkvWithGzip.putOther('testtopic01', 'key', 'value4', true);
      // ...Other defaults to no gzip since we cannot know if the specified topic is gzipped
      await kkvWithGzip.putOtherWithProducer(mockProducer, 'testtopic01', 'key', 'value5');
      await kkvWithGzip.putOtherWithProducer(mockProducer, 'testtopic01', 'key', 'value6', true);
      expect(mockProducer.mock.calls.map(v => {
        let args = v[0];
        delete args.logger;
        delete args.fetchImpl;
        return args;
      })).toEqual([
        {
          key: 'key',
          value: await compressGzipPayload('"value1"'),
          topic: 'testtopic01',
        },
        {
          key: 'key',
          value: await compressGzipPayload('"value2"'),
          topic: 'testtopic01',
        },
        {
          key: 'key',
          value: '"value3"',
          topic: 'testtopic01',
        },
        {
          key: 'key',
          value: await compressGzipPayload('"value4"'),
          topic: 'testtopic01',
        },
        {
          key: 'key',
          value: '"value5"',
          topic: 'testtopic01',
        },
        {
          key: 'key',
          value: await compressGzipPayload('"value6"'),
          topic: 'testtopic01',
        },
      ]);
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

  describe('streamValuesWhenReady', function () {

    it('waits for readiness, retries a failed stream, and resolves once the values arrived', async function () {
      jest.useFakeTimers();
      try {
        const fetchMock = jest.fn();
        const metrics = KafkaKeyValue.createMetrics(promClientMock.Counter, promClientMock.Gauge, promClientMock.Histogram);
        const kkv = new KafkaKeyValue({ cacheHost: 'http://cache-kkv', metrics, topicName: 'testtopic08', fetchImpl: fetchMock });
        const refused = Object.assign(new Error('connect ECONNREFUSED'), { errno: 'ECONNREFUSED', code: 'ECONNREFUSED' });
        const valuesResponse = () => {
          const body = new EventEmitter();
          setTimeout(() => { body.emit('data', JSON.stringify({ foo: 'bar' }) + '\n'); body.emit('end'); }, 0);
          return { status: 200, ok: true, body, headers: new Map([[LAST_SEEN_OFFSETS_HEADER_NAME, JSON.stringify([{ topic: 'testtopic08', partition: 0, offset: 9 }])]]) };
        };
        fetchMock.mockImplementation(async (url: string) => {
          if (url.endsWith('/q/health/ready')) {
            const n = fetchMock.mock.calls.filter(([u]) => u.endsWith('/q/health/ready')).length;
            if (n === 1) throw refused;
            if (n === 2) return { status: 503, text: async () => 'starting' };
            return { status: 200, text: async () => '' };
          }
          const n = fetchMock.mock.calls.filter(([u]) => u.endsWith('/cache/v1/values')).length;
          // the package's own KKV_FETCH_NUMBER_RETRIES (5) apply first, so six failures reach the outer loop once
          if (n <= 6) throw refused;
          return valuesResponse();
        });
        const seen = jest.fn();
        const done = kkv.streamValuesWhenReady(seen, { retryIntervalMs: 3000 });
        for (let i = 0; i < 8; i++) await jest.advanceTimersByTimeAsync(3000);
        await done;
        const paths = fetchMock.mock.calls.map(([u]) => (u as string).replace('http://cache-kkv', ''));
        expect(paths.slice(0, 3)).toEqual(['/q/health/ready', '/q/health/ready', '/q/health/ready']);
        expect(paths.filter(p => p === '/cache/v1/values').length).toEqual(7);
        expect(paths[paths.length - 2]).toEqual('/q/health/ready');
        expect(seen).toHaveBeenCalledWith({ foo: 'bar' });
      } finally {
        jest.useRealTimers();
      }
    });
  })

  describe('retrying a key whose fetch failed after an onupdate', function () {

    const ok = (topic: string, offset: number, value: any) => ({
      status: 200, ok: true, json: async () => value,
      headers: new Map([[LAST_SEEN_OFFSETS_HEADER_NAME, JSON.stringify([{ topic, partition: 0, offset }])]])
    });
    const reset = () => Object.assign(new Error('request failed, reason: socket hang up'), { code: 'ECONNRESET', errno: 'ECONNRESET' });
    const update = (topic: string, offset: number, keys: string[]): UpdateRequestBody =>
      ({ v: 1, topic, offsets: { '0': offset }, updates: Object.fromEntries(keys.map(k => [k, {}])) });
    // KKV_FETCH_NUMBER_RETRIES defaults to 5, so one failed refresh is six fetches
    const ATTEMPTS = KKV_FETCH_RETRY_OPTIONS.nRetries + 1;

    function setup(topic: string, config: Partial<IKafkaKeyValue> = {}) {
      const fetchMock = jest.fn();
      const metrics = KafkaKeyValue.createMetrics(promClientMock.Counter, promClientMock.Gauge, promClientMock.Histogram);
      const kkv = new KafkaKeyValue({ cacheHost: 'http://cache-kkv', metrics, topicName: topic, fetchImpl: fetchMock, updateRetry: { baseMs: 5000, maxMs: 20000 }, ...config });
      const handler = jest.fn();
      kkv.onUpdate(handler);
      // @ts-expect-error
      const warn: jest.SpyInstance<any, [any, string]> = jest.spyOn(kkv.logger, 'warn');
      // @ts-expect-error
      const error: jest.SpyInstance<any, [any, string]> = jest.spyOn(kkv.logger, 'error');
      return { kkv, fetchMock, handler, warn, error };
    }

    // The fetch's own 1 ms retries (KKV_FETCH_RETRY_INTERVAL_MS in npm test) need the fake clock to move
    const settle = async (promise: Promise<void>) => { await jest.advanceTimersByTimeAsync(50); await promise; };
    const retryMsg = 'Update for key failed, value stays as it was until the retry succeeds';

    beforeEach(() => jest.useFakeTimers());
    afterEach(() => jest.useRealTimers());

    it('retries with backoff until the fetch succeeds, then delivers', async function () {
      const { kkv, fetchMock, handler, warn } = setup('testtopic10');
      let failing = true;
      fetchMock.mockImplementation(async () => { if (failing) throw reset(); return ok('testtopic10', 7, { v: 7 }); });

      await settle(kkv.updateListener(update('testtopic10', 7, ['k'])));
      expect(fetchMock).toHaveBeenCalledTimes(ATTEMPTS);
      expect(kkv.pendingUpdateCount).toEqual(1);
      expect(warn.mock.calls.filter(([, msg]) => msg === retryMsg).map(([o]) => [o.attempts, o.retryInMs])).toEqual([[1, 5000]]);

      await jest.advanceTimersByTimeAsync(5000 + 50);
      expect(fetchMock).toHaveBeenCalledTimes(2 * ATTEMPTS);
      expect(warn.mock.calls.filter(([, msg]) => msg === retryMsg).map(([o]) => [o.attempts, o.retryInMs])).toEqual([[1, 5000], [2, 10000]]);

      await jest.advanceTimersByTimeAsync(10000 + 50);
      expect(fetchMock).toHaveBeenCalledTimes(3 * ATTEMPTS);
      expect(warn.mock.calls.filter(([, msg]) => msg === retryMsg).map(([o]) => [o.attempts, o.retryInMs])).toEqual([[1, 5000], [2, 10000], [3, 20000]]);

      await jest.advanceTimersByTimeAsync(20000 + 50);
      // capped at maxMs
      expect(warn.mock.calls.filter(([, msg]) => msg === retryMsg).map(([o]) => o.retryInMs)).toEqual([5000, 10000, 20000, 20000]);

      failing = false;
      await jest.advanceTimersByTimeAsync(20000 + 50);
      expect(handler.mock.calls).toEqual([['k', { v: 7 }]]);
      expect(kkv.pendingUpdateCount).toEqual(0);
      const before = fetchMock.mock.calls.length;
      await jest.advanceTimersByTimeAsync(120000);
      expect(fetchMock).toHaveBeenCalledTimes(before);
      kkv.close();
    });

    it('a newer update for a pending key is fetched at once and replaces the retry', async function () {
      const { kkv, fetchMock, handler } = setup('testtopic11');
      let failing = true;
      fetchMock.mockImplementation(async () => { if (failing) throw reset(); return ok('testtopic11', 8, { v: 8 }); });
      await settle(kkv.updateListener(update('testtopic11', 7, ['k'])));
      expect(kkv.pendingUpdateCount).toEqual(1);
      failing = false;
      await settle(kkv.updateListener(update('testtopic11', 8, ['k'])));
      expect(handler.mock.calls).toEqual([['k', { v: 8 }]]);
      expect(kkv.pendingUpdateCount).toEqual(0);
      const before = fetchMock.mock.calls.length;
      await jest.advanceTimersByTimeAsync(120000);
      expect(fetchMock).toHaveBeenCalledTimes(before);
      kkv.close();
    });

    it('keeps delivering the other keys of the same update', async function () {
      const { kkv, fetchMock, handler } = setup('testtopic12');
      fetchMock.mockImplementation(async (url: string) => { if (url.endsWith('/bad')) throw reset(); return ok('testtopic12', 7, { key: url.split('/').pop() }); });
      await settle(kkv.updateListener(update('testtopic12', 7, ['bad', 'good'])));
      expect(handler.mock.calls).toEqual([['good', { key: 'good' }]]);
      expect(kkv.pendingUpdateCount).toEqual(1);
      kkv.close();
    });

    it('a 404 that survived the fetch retries is an answer, logged and not retried', async function () {
      const { kkv, fetchMock, handler, error } = setup('testtopic13');
      fetchMock.mockResolvedValue({ status: 404, ok: false, headers: new Map([]) });
      await settle(kkv.updateListener(update('testtopic13', 7, ['gone'])));
      expect(fetchMock).toHaveBeenCalledTimes(ATTEMPTS);
      expect(kkv.pendingUpdateCount).toEqual(0);
      expect(error.mock.calls[0][0].err).toBeInstanceOf(NotFoundError);
      await jest.advanceTimersByTimeAsync(120000);
      expect(fetchMock).toHaveBeenCalledTimes(ATTEMPTS);
      expect(handler).not.toHaveBeenCalled();
      kkv.close();
    });

    it('updateRetry: false keeps the 1.8 behaviour, stale until the next update names the key', async function () {
      const { kkv, fetchMock, error } = setup('testtopic14', { updateRetry: false });
      fetchMock.mockImplementation(async () => { throw reset(); });
      await settle(kkv.updateListener(update('testtopic14', 7, ['k'])));
      expect(fetchMock).toHaveBeenCalledTimes(ATTEMPTS);
      expect(kkv.pendingUpdateCount).toEqual(0);
      expect(error).toHaveBeenCalledTimes(1);
      await jest.advanceTimersByTimeAsync(120000);
      expect(fetchMock).toHaveBeenCalledTimes(ATTEMPTS);
      // the same offset pushed again fetches again
      await settle(kkv.updateListener(update('testtopic14', 7, ['k'])));
      expect(fetchMock).toHaveBeenCalledTimes(2 * ATTEMPTS);
      kkv.close();
    });

    it('close() cancels the scheduled retry', async function () {
      const { kkv, fetchMock } = setup('testtopic15');
      fetchMock.mockImplementation(async () => { throw reset(); });
      await settle(kkv.updateListener(update('testtopic15', 7, ['k'])));
      kkv.close();
      await jest.advanceTimersByTimeAsync(120000);
      expect(fetchMock).toHaveBeenCalledTimes(ATTEMPTS);
    });
  })

  describe('connections', function () {

    it('opens a new connection for every request, so a retry re-enters the Service load balancing', async function () {
      // node-fetch 2 sends Connection: close whenever no agent is given, and turns
      // agent: false into no agent at all. This pins the behaviour a retry against a
      // multi-replica kkv Service relies on; pooling would pin every retry to the
      // replica that just failed.
      const http = await import('http');
      const ports: number[] = [];
      const srv = http.createServer((req, res) => {
        ports.push(req.socket.remotePort as number);
        if (ports.length < 3) { res.statusCode = 503; res.end('busy'); return; }
        res.setHeader(LAST_SEEN_OFFSETS_HEADER_NAME, JSON.stringify([{ topic: 't', partition: 0, offset: 1 }]));
        res.end(JSON.stringify({ ok: true }));
      });
      await new Promise<void>(resolve => srv.listen(0, resolve));
      const address = srv.address() as { port: number };
      const metrics = KafkaKeyValue.createMetrics(promClientMock.Counter, promClientMock.Gauge, promClientMock.Histogram);
      const kkv = new KafkaKeyValue({ cacheHost: `http://127.0.0.1:${address.port}`, metrics, topicName: 't' });
      try {
        // a 503 is not retried by get itself; three plain gets stand in for three attempts
        await expect(kkv.get('k')).rejects.toThrow('Unknown status response: 503');
        await expect(kkv.get('k')).rejects.toThrow('Unknown status response: 503');
        await expect(kkv.get('k')).resolves.toEqual({ ok: true });
        expect(new Set(ports).size).toEqual(3);
      } finally {
        kkv.close();
        srv.close();
      }
    });
  })
});