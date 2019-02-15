package se.yolean.kafka.keyvalue.metrics;

import org.apache.kafka.common.MetricName;

/**
 * WIP matching and/or mapping to exported metrics.
 */
public class KafkaMetricToPrometheus {

  private String kafkaName;
  private String kafkaGroup;

  public KafkaMetricToPrometheus(String kafkaGroup, String kafkaName) {
    if (kafkaName == null) {
      throw new IllegalArgumentException("Metric name can not be null");
    }
    if (kafkaGroup == null) {
      throw new IllegalArgumentException("Metric group can not be null");
    }
    this.kafkaName = kafkaName;
    this.kafkaGroup = kafkaGroup;
  }

  /**
   * Note: can not be used to .get from the metrics map, because equals here is never called.
   */
  @Override
  public boolean equals(Object other) {
    if (other == null) return false;
    if (MetricName.class.isInstance(other)) {
      MetricName metricName = (MetricName) other;
      return kafkaGroup.equals(metricName.group()) && kafkaName.equals(metricName.name());
    }
    return false;
  }

}
