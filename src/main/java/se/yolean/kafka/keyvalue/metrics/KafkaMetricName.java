package se.yolean.kafka.keyvalue.metrics;

import org.apache.kafka.common.MetricName;

/**
 * WIP matching and/or mapping to exported metrics.
 */
public class KafkaMetricName {

  private String kafkaName;
  private String kafkaGroup;

  public KafkaMetricName(String kafkaGroup, String kafkaName) {
    if (kafkaName == null) {
      throw new IllegalArgumentException("Metric name can not be null");
    }
    if (kafkaGroup == null) {
      throw new IllegalArgumentException("Metric group can not be null");
    }
    this.kafkaName = kafkaName;
    this.kafkaGroup = kafkaGroup;
  }

  boolean equals(MetricName kafkaMetricName) {
    return kafkaGroup.equals(kafkaMetricName.group()) && kafkaName.equals(kafkaMetricName.name());
  }

  @Override
  public boolean equals(Object other) {
    if (MetricName.class.isInstance(other)) {
      MetricName kafkaMetricName = (MetricName) other;
      return kafkaGroup.equals(kafkaMetricName.group()) && kafkaName.equals(kafkaMetricName.name());
    }
    return toString().equals(other);
  }

  @Override
  public int hashCode() {
    return this.toString().hashCode();
  }

  @Override
  public String toString() {
    return this.kafkaGroup + ":" + this.kafkaName;
  }

  public String getKafkaName() {
    return kafkaName;
  }

  public String getKafkaGroup() {
    return kafkaGroup;
  }


}
