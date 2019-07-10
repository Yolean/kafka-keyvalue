import KafkaKeyValue, { getOnUpdateRoute, ON_UPDATE_DEFAULT_PATH,  } from './';
import * as promClient from 'prom-client';
import * as express from 'express';

describe('waiting for initial cache readiness', function () {

  let server;
  beforeAll(function (done) {
    const app = express();
    app.get(ON_UPDATE_DEFAULT_PATH, getOnUpdateRoute());
    server = app.listen(80, done);
  });

  it('works', async function () {
    const cache = new KafkaKeyValue({
      cacheHost: 'http://localhost:8091',
      metrics: KafkaKeyValue.createMetrics(promClient.Counter, promClient.Gauge, promClient.Histogram),
      pixyHost: 'http://pixy',
      topicName: 'sidecar-itest'
    });

    await cache.onReady();
  });
});