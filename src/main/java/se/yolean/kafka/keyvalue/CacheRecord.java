package se.yolean.kafka.keyvalue;

import java.io.Serializable;
import java.util.Arrays;

public final class CacheRecord implements Serializable {

  private static final long serialVersionUID = 1L;

  private final byte[] value;
  private final long timestamp;

  CacheRecord(byte[] value, UpdateRecord update) {
    this.value = value;
    this.timestamp = update.getTimestamp();
  }

  public byte[] getValue() {
    return value;
  }

  public String getVstr() {
    return new String(getValue());
  }

  /**
   * @return kafka's record timestamp, regardless of org.apache.kafka.common.record.TimestampType value
   */
  public long getTimestamp() {
    return timestamp;
  }

  public String getTstr() {
    return Long.toString(getTimestamp());
  }

  @Override
  public String toString() {
    return value.length + "@" + timestamp;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + (int) (timestamp ^ (timestamp >>> 32));
    result = prime * result + Arrays.hashCode(value);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    CacheRecord other = (CacheRecord) obj;
    if (timestamp != other.timestamp)
      return false;
    if (!Arrays.equals(value, other.value))
      return false;
    return true;
  }

}
