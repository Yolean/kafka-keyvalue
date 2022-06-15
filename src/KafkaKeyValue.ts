import fetch, { RequestInfo, RequestInit, Response } from 'node-fetch';
import { Counter, Gauge, CounterConfiguration, GaugeConfiguration, HistogramConfiguration, Histogram } from 'prom-client';
import getLogger from './logger';
import { gunzip, gzip, InputType } from 'zlib';
import { promisify } from 'util';
import updateEvents from './update-events';

const pGunzip = promisify<InputType, Buffer>(gunzip);
const pGzip = promisify<InputType, Buffer>(gzip);

export interface IKafkaKeyValueImpl { new (options: IKafkaKeyValue): KafkaKeyValue }

export interface IKafkaKeyValue {
  topicName: string
  cacheHost: string
  pixyHost: string
  gzip?: boolean
  metrics: IKafkaKeyValueMetrics
  fetchImpl?: IFetchImpl
}

export interface CounterConstructor {
  new(options: CounterConfiguration<string>): Counter<string>
}

export interface GaugeConstructor {
  new(options: GaugeConfiguration<string>): Gauge<string>
}

export interface HistogramConstructor {
  new(options: HistogramConfiguration<string>): Histogram<string>
}

export interface IKafkaKeyValueMetrics {
  kafka_key_value_last_seen_offset: Gauge<string>
  kafka_key_value_get_latency_seconds: Histogram<string>
  kafka_key_value_parse_latency_seconds: Histogram<string>
  kafka_key_value_stream_latency_seconds: Histogram<string>
}

export interface PixyPostTopicKeySyncResponse {
  offset: number
}

export type UpdateHandler = (key: string, value: any) => any

export class NotFoundError extends Error {
  notFound = true
}

export async function decompressGzipResponse(logger, buffer: Buffer): Promise<any> {
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

async function parseResponse(logger, res: Response, assumeGzipped: boolean): Promise<any> {
  if (assumeGzipped) return decompressGzipResponse(logger, await res.buffer());
  else return res.json();
}

async function produceViaPixy(fetchImpl: IFetchImpl, logger, pixyHost: string, topic: string, key: string, value: any, gzip: boolean) {
  const stringValue: string = JSON.stringify(value);
  let valueReady: Promise<string | Buffer> = Promise.resolve(stringValue);
  if (gzip) valueReady = compressGzipPayload(stringValue);

  const res = await fetchImpl(`${pixyHost}/topics/${topic}/messages?key=${key}&sync`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: await valueReady
  });

  if (res.status !== 200) {
    throw new Error('Invalid statusCode: ' + res.status);
  }

  const json = await res.json() as PixyPostTopicKeySyncResponse;
  logger.debug({ res, json }, 'KafkaCache put returned');

  return json.offset;
}

export async function streamResponseBody(logger, body: NodeJS.ReadableStream, onValue: (value: any) => void) {
  return new Promise<void>((resolve, reject) => {

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

export type IFetchImpl = (url: RequestInfo, init?: RequestInit | undefined) => Promise<Response>;

export interface IRetryOptions {
  nRetries: number,
  intervalMs: number
}

function getFetchImpl(config: IKafkaKeyValue): IFetchImpl {
  let fetchImpl = config.fetchImpl;
  if (fetchImpl) return fetchImpl;
  else return fetch;
}

async function retryTimes<T>(fn: () => Promise<T>, options: IRetryOptions): Promise<T> {
  try {
    return await fn();
  } catch (err) {
    if (options.nRetries === 0) throw err;

    await new Promise(resolve => setTimeout(resolve, options.intervalMs));
    return retryTimes(fn, { nRetries: options.nRetries - 1, intervalMs: options.intervalMs });
  }
}

export const PUT_RETRY_DEFAULTS: IRetryOptions = {
  nRetries: 10,
  intervalMs: 1000
};

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
  private readonly fetchImpl: IFetchImpl;
  private readonly logger;

  constructor(config: IKafkaKeyValue) {
    this.config = config;
    this.topic = config.topicName;
    this.metrics = config.metrics;
    this.fetchImpl = getFetchImpl(config);
    this.logger = getLogger({ name: `kkv:${this.getCacheName()}` });

    updateEvents.on('update', async (requestBody) => {
      if (requestBody.v !== 1) throw new Error(`Unknown kkv onupdate protocol ${requestBody.v}!`);

      const {
        topic, offsets, updates
      } = requestBody;

      const expectedTopic = this.topic;
      this.logger.debug({ topic, expectedTopic }, 'Matching update event against expected topic');
      if (topic !== expectedTopic) {
        this.logger.debug({ topic, expectedTopic }, 'Update event ignored due to topic mismatch. Business as usual.');
        return;
      }

      if (this.updateHandlers.length > 0) {
        const updatesBeingPropagated = Object.keys(updates).map(async key => {
          this.logger.debug({ key }, 'Received update event for key');
          const value = await this.get(key);
          this.updateHandlers.forEach(fn => fn(key, value));
        });

        await Promise.all(updatesBeingPropagated);
      } else {
        this.logger.debug({ topic }, 'No update handlers registered, update event has no effect');
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

    this.logger.info({ attempt, cacheHost: this.getCacheHost() }, 'Polling cache for readiness');
    let res;
    try {
      res = await this.fetchImpl(this.getCacheHost() + '/health/ready', {
        headers: { 'Content-Type': 'application/json' }
      });
    } catch (e) {
      if (e.errno === 'ECONNREFUSED') {
        this.logger.info({ err: e }, 'Identified as retryable');
        return retry();
      }
      throw e;
    }

    if (res.status !== 200) {
      this.logger.info({ responseBody: await res.text(), statusCode: res.status }, 'Cache not ready yet');
      return retry();
    }

    this.logger.info('200 received, cache ready');
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
    const res = await this.fetchImpl(`${this.getCacheHost()}/cache/v1/raw/${key}`);
    httpGetTiming();

    const parseTiming = this.metrics.kafka_key_value_parse_latency_seconds.startTimer({ cache_name: this.getCacheName() });

    if (res.status === 404) {
      throw new NotFoundError('Cache does not contain key: ' + key);
    } else if (!res.ok) {
      const msg = 'Unknown status response: ' + res.status;
      this.logger.error({ res }, msg);
      throw new Error(msg);
    }

    const value = parseResponse(this.logger, res, this.config.gzip || false);

    parseTiming();
    this.logger.debug({ key, value }, 'KafkaCache get value returned')
    return value;
  }

  async streamValues(onValue: (value: any) => void): Promise<void> {
    if (this.config.gzip) throw new Error('Unsuported method for gzipped topics!');
    this.logger.debug({ cache_name: this.getCacheName() }, 'Streaming values for cache started');

    const streamTiming = this.metrics.kafka_key_value_stream_latency_seconds.startTimer({ cache_name: this.getCacheName() });
    const res = await this.fetchImpl(`${this.getCacheHost()}/cache/v1/values`);

    if (res.body === null) return Promise.reject('Received null body');

    await streamResponseBody(this.logger, res.body, onValue);

    streamTiming();
    this.logger.debug({ cache_name: this.getCacheName() }, 'Streaming values for cache finished');
  }

  async put(key: string, value: any, options: IRetryOptions = PUT_RETRY_DEFAULTS): Promise<number> {
    return retryTimes<number>(() => produceViaPixy(this.fetchImpl, this.logger, this.getPixyHost(), this.topic, key, value, this.config.gzip || false), options);
  }

  async putOther(topic: string, key: string, value: any, gzip = false, options: IRetryOptions = PUT_RETRY_DEFAULTS): Promise<number> {
    return retryTimes<number>(() => produceViaPixy(this.fetchImpl, this.logger, this.getPixyHost(), topic, key, value, gzip), options);
  }

  on(event: 'put', fn: UpdateHandler): void {
    this.onUpdate(fn);
  }

  onUpdate(fn: UpdateHandler) {
    this.updateHandlers.push(fn);
  }
}
