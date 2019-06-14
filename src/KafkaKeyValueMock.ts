import KafkaKeyValue from "./KafkaKeyValue";

const globalMap: Map<string, any> = new Map();

export class KafkaKeyValueMock extends KafkaKeyValue {

  private mockOffset = 0;

  async get(key: string): Promise<any> {
    return globalMap.get(key);
  }

  async put(key: string, value: any): Promise<number> {
    return Promise.resolve(this.mockOffset++);
  }

  static async putGlobal(key: string, value: any) {
    globalMap.set(key, value);
  }
}