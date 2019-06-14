import KafkaKeyValue, { streamResponseBody } from './KafkaKeyValue';
import updateEvents from './update-events';
import { EventEmitter } from 'events';

const promClientMock = {
  Counter: class Counter {
    inc: any
    dec: any
    labels: any
    reset: any

    constructor(options) {

      this.inc = jest.fn();
      this.dec = jest.fn();
      this.labels = jest.fn().mockReturnValue(this);
      this.reset = jest.fn();
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

    constructor(options) {

      this.inc = jest.fn();
      this.dec = jest.fn();
      this.set = jest.fn();
      this.labels = jest.fn().mockReturnValue(this);
      this.reset = jest.fn();
      this.setToCurrentTime = jest.fn();
      this.startTimer = jest.fn();
    }
  },

  Histogram: class Histogram {
    observe: any
    startTimer: any
    labels: any
    reset: any

    constructor(options) {

      this.observe = jest.fn();
      this.startTimer = jest.fn();
      this.labels = jest.fn().mockReturnValue(this);
      this.reset = jest.fn();
    }
  },
};

describe('KafkaKeyValue', function () {

  describe('streaming values', function () {

    it('was tricky apparently', async function () {
      const bodyStream = new EventEmitter();


      const onValue = jest.fn();
      const streamCompleted = streamResponseBody(<any>bodyStream, onValue);

      bodyStream.emit('data', JSON.stringify({ foo: 'bar' }) + '\n');
      bodyStream.emit('data', JSON.stringify({ foo: 'bar2' }).substr(0, 4));
      bodyStream.emit('data', JSON.stringify({ foo: 'bar2' }).substr(4, 11) + '\n');
      bodyStream.emit('end');

      await streamCompleted;
      expect(onValue).toHaveBeenCalledTimes(2);
      expect(onValue).toBeCalledWith({ foo: 'bar' })
      expect(onValue).toBeCalledWith({ foo: 'bar2' })
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
      await Promise.resolve();
      await Promise.resolve();

      expect(onUpdateSpy).toHaveBeenCalledTimes(1);
      expect(onUpdateSpy).toHaveBeenCalledWith('bd3f6188-d865-443d-8646-03e8f1c643cb', { foo: 'bar' });

      expect(metrics.kafka_key_value_last_seen_offset.set).toHaveBeenCalledTimes(1);
      expect(metrics.kafka_key_value_last_seen_offset.labels).toHaveBeenCalledTimes(1);
      expect(metrics.kafka_key_value_last_seen_offset.labels).toHaveBeenCalledWith('cache-kkv', 'testtopic01', '0');
      expect(metrics.kafka_key_value_last_seen_offset.set).toHaveBeenCalledWith(28262);
    });
  });
});