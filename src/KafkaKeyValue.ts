import fetch, { Response } from 'node-fetch';
import { Counter, Gauge, CounterConfiguration, GaugeConfiguration, HistogramConfiguration, Histogram } from 'prom-client';
import getLogger from './logger';
import { gunzip, gzip, InputType } from 'zlib';
import { promisify } from 'util';
import updateEvents from './update-events';

const logger = getLogger({ __filename });
const pGunzip = promisify<InputType, Buffer>(gunzip);
const pGzip = promisify<InputType, Buffer>(gzip);

export interface IKafkaKeyValueImpl { new (options: IKafkaKeyValue): KafkaKeyValue }

export interface IKafkaKeyValue {
  topicName: string
  cacheHost: string
  pixyHost: string
  gzip?: boolean
  metrics: IKafkaKeyValueMetrics
}

export interface CounterConstructor {
  new(options: CounterConfiguration): Counter
}

export interface GaugeConstructor {
  new(options: GaugeConfiguration): Gauge
}

export interface HistogramConstructor {
  new(options: HistogramConfiguration): Histogram
}

export interface IKafkaKeyValueMetrics {
  kafka_key_value_last_seen_offset: Gauge
  kafka_key_value_get_latency_seconds: Histogram
  kafka_key_value_parse_latency_seconds: Histogram
  kafka_key_value_stream_latency_seconds: Histogram
}

export type UpdateHandler = (key: string, value: any) => any

export async function decompressGzipResponse(buffer: Buffer): Promise<any> {
  let msg;
  try {
    msg = await pGunzip(buffer);
  } catch (err) {
    logger.error({ err, bufferLength: buffer.length }, 'Failed decompress buffer through gzip');
    throw err;
  }

  try {
    return JSON.parse(msg);
  } catch (err) {
    logger.error({ err, string: msg, bufferLength: buffer.length }, 'Failed to parse string into json');
    throw err;
  }
}

export async function compressGzipPayload(payload: string): Promise<Buffer> {
  return pGzip(payload);
}

async function parseResponse(res: Response, assumeGzipped: boolean): Promise<any> {
  if (assumeGzipped) return decompressGzipResponse(await res.buffer());
  else return res.json();
}

export async function streamResponseBody(body: NodeJS.ReadableStream, onValue: (value: any) => void) {
  return new Promise((resolve, reject) => {

    let payload = '';

    body.on('data', (data: Buffer) => payload += data.toString());
    body.on('end', () => {
      const values = payload.trim().split('\n');
      values.forEach(str => {
        let value;
        try {
          value = JSON.parse(str);
        } catch (err) {
          logger.error({ err, str }, 'Failed to parser string into JSON!');
          throw err;
        }
        onValue(value);
      });

      resolve();
    });
    body.on('error', reject);
  });
}

export default class KafkaKeyValue {

  static createMetrics(counterCtr: CounterConstructor, gaugeCtr: GaugeConstructor, histogramCtr: HistogramConstructor): IKafkaKeyValueMetrics {
    return {
      kafka_key_value_last_seen_offset: new gaugeCtr({
        name: 'kafka_key_value_last_seen_offset',
        help: 'Last seen offset for this pod under this topic',
        labelNames: ['cache_name', 'topic', 'partition']
      }),
      kafka_key_value_get_latency_seconds: new histogramCtr({
        name: 'kafka_key_value_get_latency_seconds',
        help: 'Latency in seconds for the HTTP get requests to the kafka-streams cache',
        labelNames: ['cache_name']
      }),
      kafka_key_value_parse_latency_seconds: new histogramCtr({
        name: 'kafka_key_value_parse_latency_seconds',
        help: 'Latency in seconds for parsing the value [to json objects]',
        labelNames: ['cache_name']
      }),
      kafka_key_value_stream_latency_seconds: new histogramCtr({
        name: 'kafka_key_value_stream_latency_seconds',
        help: 'Latency in seconds for streaming all values from the http cache (includes parsing)',
        labelNames: ['cache_name']
      })
    }
  }

  private readonly topic: string
  private readonly config: IKafkaKeyValue
  private readonly updateHandlers: UpdateHandler[] = [];
  private readonly metrics: IKafkaKeyValueMetrics;

  constructor(config: IKafkaKeyValue) {
    this.config = config;
    this.topic = config.topicName;
    this.metrics = config.metrics;

    updateEvents.on('update', async (requestBody) => {
      if (requestBody.v !== 1) throw new Error(`Unknown kkv onupdate protocol ${requestBody.v}!`);

      const {
        topic, offsets, updates
      } = requestBody;

      const expectedTopic = this.topic;
      logger.debug({ topic, expectedTopic }, 'Matching update event against expected topic');
      if (topic !== expectedTopic) {
        logger.debug({ topic, expectedTopic }, 'Update event ignored due to topic mismatch. Business as usual.');
        return;
      }

      if (this.updateHandlers.length > 0) {
        const updatesBeingPropagated = Object.keys(updates).map(async key => {
          logger.debug({ key }, 'Received update event for key');
          const value = await this.get(key);
          this.updateHandlers.forEach(fn => fn(key, value));
        });

        await Promise.all(updatesBeingPropagated);
      } else {
        logger.debug({ topic }, 'No update handlers registered, update event has no effect');
      }

      // NOTE: Letting all handlers complete before updating the metric
      // makes sense because that will also produce bugs, likely visible to users
      Object.entries<number>(offsets).forEach(([partition, offset]) => {
        this.metrics.kafka_key_value_last_seen_offset
          .labels(this.getCacheName(), topic, partition)
          .set(offset);
      });

      // TODO Resolve waitForOffset logic?
    });
  }

  async onReady(attempt = 1) {
    const retry = async () => {
      await new Promise(resolve => setTimeout(resolve, 3000));
      return this.onReady(attempt + 1);
    };

    logger.info({ attempt, cacheHost: this.getCacheHost() }, 'Polling cache for readiness');
    let res;
    try {
      res = await fetch(this.getCacheHost() + '/health/ready', {
        headers: { 'Content-Type': 'application/json' }
      });
    } catch (e) {
      if (e.errno === 'ECONNREFUSED') {
        logger.info({ err: e }, 'Identified as retryable');
        return retry();
      }
      throw e;
    }

    if (res.status !== 200) {
      logger.info({ responseBody: await res.text(), statusCode: res.status }, 'Cache not ready yet');
      return retry();
    }

    logger.info('200 received, cache ready');
  }

  private getCacheHost() {
    return this.config.cacheHost;
  }

  private getCacheName(): string {
    return this.config.cacheHost.replace('http://', '');
  }

  private getPixyHost() {
    return this.config.pixyHost;
  }

  async get(key: string): Promise<any> {
    // NOTE: Expects raw=json|gzipped-json
    const httpGetTiming = this.metrics.kafka_key_value_get_latency_seconds.startTimer({ cache_name: this.getCacheName() })
    const res = await fetch(`${this.getCacheHost()}/cache/v1/raw/${key}`);
    httpGetTiming();

    const parseTiming = this.metrics.kafka_key_value_parse_latency_seconds.startTimer({ cache_name: this.getCacheName() });

    if (res.status === 404) {
      const notFoundErr = new Error('Cache does not contain key: ' + key);
      // TODO
      // notFoundErr.notFound = true;
      throw notFoundErr;
    } else if (!res.ok) {
      const msg = 'Unknown status response: ' + res.status;
      logger.error({ res }, msg);
      throw new Error(msg);
    }

    const value = parseResponse(res, this.config.gzip || false);

    parseTiming();
    logger.debug({ key, value }, 'KafkaCache get value returned')
    return value;
  }

  async streamValues(onValue: (value: any) => void): Promise<void> {
    if (this.config.gzip) throw new Error('Unsuported method for gzipped topics!');
    logger.debug({ cache_name: this.getCacheName() }, 'Streaming values for cache started');

    const streamTiming = this.metrics.kafka_key_value_stream_latency_seconds.startTimer({ cache_name: this.getCacheName() });
    const res = await fetch(`${this.getCacheHost()}/cache/v1/values`);

    await streamResponseBody(res.body, onValue);

    streamTiming();
    logger.debug({ cache_name: this.getCacheName() }, 'Streaming values for cache finished');
  }

  async put(key: string, value: any): Promise<number> {
    const stringValue: string = JSON.stringify(value);
    let valueReady: Promise<string | Buffer> = Promise.resolve(stringValue);
    if (this.config.gzip) valueReady = compressGzipPayload(stringValue);

    const res = await fetch(`${this.getPixyHost()}/topics/${this.topic}/messages?key=${key}&sync`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: await valueReady
    });

    const json = await res.json();
    logger.debug({ res, json }, 'KafkaCache put returned');

    return json.offset;
  }

  on(event: 'put', fn: UpdateHandler): void {
    this.onUpdate(fn);
  }

  onUpdate(fn: UpdateHandler) {
    this.updateHandlers.push(fn);
  }
}
