package se.yolean.kafka.keyvalue;

public class UpdateRecord {

  private String topic;
  private int partition;
  private long offset;
  private byte[] key;

  public UpdateRecord(String topic, int partition, long offset, byte[] key) {
    this.topic = topic;
    this.partition = partition;
    this.offset = offset;
    this.key = key;
  }

  public String getTopic() {
    return topic;
  }

  public int getPartition() {
    return partition;
  }

  public long getOffset() {
    return offset;
  }

  public byte[] getKey() {
    return key;
  }

}
