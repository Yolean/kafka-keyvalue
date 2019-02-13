package se.yolean.kafka.keyvalue;

public class UpdateRecord {

  private int partition;
  private long offset;
  private byte[] key;

  public UpdateRecord(int partition, long offset, byte[] key) {
    this.partition = partition;
    this.offset = offset;
    this.key = key;
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
