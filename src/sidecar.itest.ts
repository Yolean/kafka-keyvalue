import KafkaKeyValue, { getOnUpdateRoute, ON_UPDATE_DEFAULT_PATH,  } from './';
import * as promClient from 'prom-client';
import * as express from 'express';
import { UpdateHandler } from './KafkaKeyValue';

describe('waiting for initial cache readiness', function () {

  let server, getUniqueKey;
  beforeAll(function (done) {
    jest.setTimeout(30000);
    const now = Date.now();
    getUniqueKey = (key: string) => `${key}_${now}`;

    const app = express();
    app.post(ON_UPDATE_DEFAULT_PATH, getOnUpdateRoute());
    server = app.listen(80, done);
  });

  afterAll(function (done) {
    server.close(done);
  });

  beforeEach(() => {
    promClient.register.clear();
  });

  it('is a lightweight way to let us write to any topic we can specify', async function () {

    const metrics = KafkaKeyValue.createMetrics(promClient.Counter, promClient.Gauge, promClient.Histogram);
    
    const cache1 = new KafkaKeyValue({
      cacheHost: 'http://localhost:8091',
      metrics,
      pixyHost: 'http://pixy',
      topicName: 'sidecar-itest'
    });

    await cache1.onReady();

    const cache2 = new KafkaKeyValue({
      cacheHost: 'http://localhost:8092',
      metrics,
      pixyHost: 'http://pixy',
      topicName: 'sidecar-itest-other'
    });

    await cache2.onReady();

    const key = getUniqueKey('write_to_other');

    const updateReceived = new Promise<void>(resolve => cache2.onUpdate((updatedKey, value) => {
      if (updatedKey === key) resolve();
    }));

    const updateWritten = cache1.putOther('sidecar-itest-other', key, { x: 'y' });

    await updateWritten;
    await updateReceived;

    const value = await cache2.get(key);
    expect(value).toEqual({ x: 'y' });
  });

  it('lets us put and get values afterwards', async function () {
    const cache1 = new KafkaKeyValue({
      cacheHost: 'http://localhost:8091',
      metrics: KafkaKeyValue.createMetrics(promClient.Counter, promClient.Gauge, promClient.Histogram),
      pixyHost: 'http://pixy',
      topicName: 'sidecar-itest'
    });

    await cache1.onReady();

    const onUpdateSpy: UpdateHandler = jest.fn();
    cache1.onUpdate(onUpdateSpy);
    const updateReceived = new Promise<void>(resolve => cache1.onUpdate((key, value) => {
      if (key === getUniqueKey('first_put')) resolve();
    }));

    await cache1.put(getUniqueKey('first_put'), { result: 'hole in one' });

    await updateReceived;
    expect(onUpdateSpy).toHaveBeenCalled();
    expect(onUpdateSpy).toHaveBeenCalledWith(getUniqueKey('first_put'), { result: 'hole in one' });
    expect(await cache1.get(getUniqueKey('first_put'))).toEqual({ result: 'hole in one' });
  });
});