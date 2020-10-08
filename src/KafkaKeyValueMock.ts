import KafkaKeyValue, { NotFoundError } from "./KafkaKeyValue";

const globalMap: Map<string, any> = new Map();

export class KafkaKeyValueMock extends KafkaKeyValue {

  private mockOffset = 0;

  onReady() {
    return Promise.resolve();
  }

  async get(key) {
    const value = globalMap.get(key);
    if (value === undefined) {
      throw new NotFoundError('Cache does not contain key: ' + key);
    }

    return value;
  }

  async put(key: string, value: any): Promise<number> {
    return Promise.resolve(this.mockOffset++);
  }

  static async putGlobal(key: string, value: any) {
    globalMap.set(key, value);
  }
}