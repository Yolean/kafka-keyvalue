// NOTE: Copied from internal logging module

import * as bunyan from '@yolean/bunyan';
import { join as pathJoin } from "path";

export interface PerLoggerOptions {
  __filename?: string
  name?: string
}

const appPath = pathJoin(__dirname, '../');

interface LoggerOptions extends bunyan.LoggerOptions {
  streams: bunyan.Stream[]
  name: string
  serializers: any
}

const globalOptions: LoggerOptions = {
  name: "no-logger-name-given",
  streams: [
    {
      level: <bunyan.LogLevel>(process.env.KAFKA_KEYVALUE_LOG_LEVEL || 'debug'),
      stream: process.stdout
    }
  ],
  serializers: bunyan.stdSerializers
};

export default function getLogger(options: PerLoggerOptions) {
  // we can console.warn here, but throwing now to get the init logic right
  if (globalOptions.streams.length === 0) throw new Error('Logger output stream is yet to be defined');
  let opt = { ...globalOptions };
  if (typeof options === 'string') {
    options = { __filename: options };
  }
  if (options.name) {
    opt.name = options.name;
  }
  if (options.__filename) {
    opt.name = options.__filename;
    if (opt.name.indexOf(appPath) === 0) opt.name = opt.name.substr(appPath.length);
  }
  return bunyan.createLogger(opt);
};

export { getLogger };
