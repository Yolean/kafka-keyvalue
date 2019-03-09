package se.yolean.kafka.keyvalue;

import java.io.Serializable;

import org.apache.kafka.common.TopicPartition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"topic","partition","offset","key"})
public class UpdateRecord implements Serializable {

  private static final long serialVersionUID = 1L;

  private static final long NO_TIMESTAMP = -1;

  private final TopicPartition topicPartition;
  private final long offset;
  private final String key;
  private final String string;
  private final int hashCode;

  private long timestamp = NO_TIMESTAMP;

  @JsonCreator
  public UpdateRecord(
      @JsonProperty("topic") String topic,
      @JsonProperty("partition") int partition,
      @JsonProperty("offset") long offset,
      @JsonProperty("key") String key) {
    this.topicPartition = new TopicPartition(topic, partition);
    this.offset = offset;
    this.key = key;
    this.string = topicPartition.toString() + '-' + offset + '[' + key + ']';
    this.hashCode = string.hashCode();
  }

  public UpdateRecord(String topic, int partition, long offset, String key, long timestamp) {
    this(topic, partition, offset, key);
    this.timestamp  = timestamp;
  }

  public String getTopic() {
    return topicPartition.topic();
  }

  public int getPartition() {
    return topicPartition.partition();
  }

  public long getOffset() {
    return offset;
  }

  public String getKey() {
    return key;
  }

  TopicPartition getTopicPartition() {
    return topicPartition;
  }

  /**
   * Timestamp is just a value we carry during processing, not serialized to clients
   * (at least not until we have a convincing use case for including it in onupdate).
   */
  public long getTimestamp() {
    if (timestamp == NO_TIMESTAMP) {
      throw new IllegalStateException("The optional timestamp was requested when not set, for " + this);
    }
    return timestamp;
  }

  @Override
  public String toString() {
    return string;
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  @Override
  public boolean equals(Object obj) {
    return obj != null && obj instanceof UpdateRecord && string.equals(obj.toString());
  }

}
