package se.yolean.kafka.keyvalue;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@JsonPropertyOrder({"offset", "partition", "topic"})
public class TopicPartitionOffset {

  @JsonProperty
  final String topic;
  @JsonProperty
  final Integer partition;
  @JsonProperty
  final Long offset;

  public TopicPartitionOffset(String topic, Integer partition, Long offset) {
    this.topic = topic;
    this.partition = partition;
    this.offset = offset;
  }
}