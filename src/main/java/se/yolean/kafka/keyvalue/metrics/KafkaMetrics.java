package se.yolean.kafka.keyvalue.metrics;

import static se.yolean.kafka.keyvalue.metrics.KafkaGaugeToPrometheus.getNameInPrometheus;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaMetrics {

  static final Logger logger = LoggerFactory.getLogger(KafkaMetrics.class);

  static final Map<String, KafkaGaugeToPrometheus> prometheus = new HashMap<>();

  static boolean initialized = false;

  static final String KAFKA_METRICS_COUNT =
      getNameInPrometheus("kafka-metrics-count", "count");

  private KafkaConsumer<String, byte[]> consumer;

  /**
   * @param consumer The handle to global state from KafkaStreams
   */
  public KafkaMetrics(KafkaConsumer<String, byte[]> consumer) {
    this.consumer = consumer;
    if (initialized) {
      throw new IllegalStateException("A metrics instance has been created already");
    }
    initialized = true;
  }

  /**
   * So we can trigger re-check from the main thread without thread creation in this class.
   */
  public void check() {
    Map<MetricName, ? extends Metric> kafkaMetrics = consumer.metrics();

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

    }
  }

}
