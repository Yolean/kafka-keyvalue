package se.yolean.kafka.keyvalue;

import org.apache.kafka.common.TopicPartition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"topic","partition","offset","key"})
public class UpdateRecord {

  private final TopicPartition topicPartition;
  private final long offset;
  private final String key;
  private final String string;
  private final int hashCode;

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
