package se.yolean.kafka.keyvalue.metrics;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamsMetrics {

  public static final Logger logger = LoggerFactory.getLogger(StreamsMetrics.class);

  private static final Map<MetricName, KafkaGaugeToPrometheus> prometheus = new HashMap<>();

  private static final KafkaMetricName ASSIGNED_PARTITIONS =
      new KafkaMetricName("consumer-coordinator-metrics", "assigned-partitions");

  // These might be interesting too for health
  private static final KafkaMetricName COMMIT_TOTAL =
      new KafkaMetricName("consumer-coordinator-metrics", "commit-total");
  private static final KafkaMetricName RECORDS_CONSUMED_TOTAL =
      new KafkaMetricName("consumer-fetch-manager-metrics", "records-consumed-total");

  private Map<MetricName, ? extends Metric> kafkaMetrics;

  private boolean hasSeenAssignedParititions = false;

  /**
   * @param metrics The handle to global state from KafkaStreams
   */
  public StreamsMetrics(Map<MetricName, ? extends Metric> metrics) {
    this.kafkaMetrics = metrics;
  }

  /**
   * So we can trigger re-check from the main thread without thread cretion in this class.
   */
  public void check() {
    for (Map.Entry<MetricName, ? extends Metric> metric : kafkaMetrics.entrySet()) {
      KafkaGaugeToPrometheus prom = prometheus.get(metric.getKey());
      if (prom == null) {
        prom = new KafkaGaugeToPrometheus(metric.getKey());
        prometheus.put(metric.getKey(), prom);
        logger.info("Found new metric {}, created Prometheus metric {}", metric.getKey(), prom);
      }
      prom.update(metric.getValue());
      if (!hasSeenAssignedParititions && ASSIGNED_PARTITIONS.equals(prom)) {
        Double partitions = (Double) metric.getValue().metricValue();
        if (partitions > 0.5) {
          hasSeenAssignedParititions = true;
          logger.info("Noticed assigned partitions for the first time");
        }
      }
    }
  }

  public boolean hasSeenAssignedParititions() {
    return hasSeenAssignedParititions;
  }

  public void checkOnPrometheusScrape() {
    check();
  }

}
