package se.yolean.kafka.keyvalue.metrics;

import static se.yolean.kafka.keyvalue.metrics.KafkaGaugeToPrometheus.getNameInPrometheus;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamsMetrics {

  public static final Logger logger = LoggerFactory.getLogger(StreamsMetrics.class);

  private static final Map<String, KafkaGaugeToPrometheus> prometheus = new HashMap<>();

  private static final String ASSIGNED_PARTITIONS =
      getNameInPrometheus("consumer-coordinator-metrics", "assigned-partitions");
  private static final String KAFKA_METRICS_COUNT =
      getNameInPrometheus("kafka-metrics-count", "count");

  // These might be interesting too for health
  private static final String COMMIT_TOTAL =
      getNameInPrometheus("consumer-coordinator-metrics", "commit-total");
  private static final String RECORDS_CONSUMED_TOTAL =
      getNameInPrometheus("consumer-fetch-manager-metrics", "records-consumed-total");

  private Map<MetricName, ? extends Metric> kafkaMetrics;

  private boolean hasSeenAssignedParititions = false;

  /**
   * @param metrics The handle to global state from KafkaStreams
   */
  public StreamsMetrics(Map<MetricName, ? extends Metric> metrics) {
    this.kafkaMetrics = metrics;
  }

  /**
   * So we can trigger re-check from the main thread without thread creation in this class.
   */
  public void check() {
    for (Map.Entry<MetricName, ? extends Metric> metric : kafkaMetrics.entrySet()) {

      String promName = getNameInPrometheus(metric.getKey());

      if (KAFKA_METRICS_COUNT.equals(promName)) continue;

      KafkaGaugeToPrometheus prom = prometheus.get(promName);
      if (prom == null) {
        prom = new KafkaGaugeToPrometheus(metric.getKey());
        prometheus.put(promName, prom);
        logger.trace("Found new metric {}, created Prometheus metric {}", metric.getKey(), prom);
      }

      prom.update(metric.getValue());

      if (!hasSeenAssignedParititions && ASSIGNED_PARTITIONS.equals(promName)) {
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
