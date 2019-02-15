package se.yolean.kafka.keyvalue.metrics;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamsMetrics {

  public static final Logger logger = LoggerFactory.getLogger(StreamsMetrics.class);

  private static final KafkaMetricToPrometheus ASSIGNED_PARTITIONS =
      new KafkaMetricToPrometheus("consumer-coordinator-metrics", "assigned-partitions");

  // These might be interesting too for health
  private static final KafkaMetricToPrometheus COMMIT_TOTAL =
      new KafkaMetricToPrometheus("consumer-coordinator-metrics", "commit-total");
  private static final KafkaMetricToPrometheus RECORDS_CONSUMED_TOTAL =
      new KafkaMetricToPrometheus("consumer-fetch-manager-metrics", "records-consumed-total");

  private Map<MetricName, ? extends Metric> metrics;

  private boolean hasSeenAssignedParititions = false;

  /**
   * @param metrics The handle to global state from KafkaStreams
   */
  public StreamsMetrics(Map<MetricName, ? extends Metric> metrics) {
    this.metrics = metrics;
  }

  /**
   * So we can trigger re-check frorm the main thread without being clever here, yet.
   * Possibly obsolete when we integrate with prometheus.
   */
  public void check() {
    for (MetricName metric : metrics.keySet()) {
      Object metricValue = metrics.get(metric).metricValue();
      if (!hasSeenAssignedParititions && ASSIGNED_PARTITIONS.equals(metric)) {
        Double partitions = (Double) metricValue;
        if (partitions > 0.5) {
          hasSeenAssignedParititions = true;
          logger.info("Noticed assigned partitions for the first time");
        }
      }
      String name = metric.name();
      String group = metric.group();
      if (name.contains("-rate")) {
        logger.trace("{}:{}={} #{}", group, name, metricValue, metric.description());
      } else if (group.startsWith("admin-client-")) {
        logger.trace("{}:{}={} #{}", group, name, metricValue, metric.description());
      } else {
        logger.debug("{}:{}={} #{}", group, name, metricValue, metric.description());
      }
    }
  }

  public boolean hasSeenAssignedParititions() {
    return hasSeenAssignedParititions;
  }

}
