import * as bodyParser from 'body-parser';

import KafkaKeyValue from './KafkaKeyValue';
import updateEvents from './update-events';
import getLogger from './logger';
import { Request, Response } from 'express';

const logger = getLogger({ __filename });

export const ON_UPDATE_DEFAULT_PATH = '/kafka-keyvalue/v1/updates';
// NOTE: adding any as return type for getOnUpdateRoute fixed:
// https://stackoverflow.com/questions/43335336/error-ts4058-return-type-of-exported-function-has-or-is-using-name-x-from-exter
export function getOnUpdateRoute(): any {
  return [bodyParser.json({}), (req: Request, res: Response) => {
    const body = req.body;
    logger.trace({
      remoteAddress: req.connection && req.connection.remoteAddress,
      topic: req.get('x-kkv-topic'),
      offsets: req.get('x-kkv-offsets'),
    }, 'Incoming onupdate webhook');
    updateEvents.emit('update', body);
    res.sendStatus(204);
  }];
}

export default KafkaKeyValue;
export * from './KafkaKeyValue';
export { KafkaKeyValueMock } from './KafkaKeyValueMock';
