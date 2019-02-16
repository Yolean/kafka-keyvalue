package se.yolean.kafka.keyvalue.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.prometheus.client.Gauge;

public class KafkaGaugeToPrometheus extends KafkaMetricName {

  private static final Logger logger = LoggerFactory.getLogger(KafkaGaugeToPrometheus.class);

  private Gauge gauge = null;

  private List<String> labels = null;

  private long lastUpdated = 0;

  public KafkaGaugeToPrometheus(String kafkaGroup, String kafkaName) {
    super(kafkaGroup, kafkaName);
  }

  public KafkaGaugeToPrometheus(MetricName metricName) {
    this(metricName.group(), metricName.name());
  }

  private void setup(MetricName name) {
    List<String> labels = getLabelNames(name);
    gauge = Gauge.build()
        .name(name.name().replace('-', '_'))
        .labelNames(labels.toArray(new String[labels.size()]))
        .help(name.description())
        .register();
    this.labels = Collections.unmodifiableList(labels);
    logger.info("New prometheus metric created: {} from {}", this, name);
  }

  private List<String> getLabelNames(MetricName name) {
    ArrayList<String> labels = new ArrayList<>(name.tags().size());
    labels.add("kafka_metric_group");
    name.tags().keySet().forEach(key -> labels.add(key.replace('-', '_')));
    return labels;
  }

  private List<String> getLabelValues(MetricName name) {
    ArrayList<String> labels = new ArrayList<>(name.tags().size());
    labels.add(name.group());
    name.tags().values().forEach(value -> labels.add(value));
    return labels;
  }

  public void update(Metric metric) {
    MetricName name = metric.metricName();
    if (!super.equals(name)) {
      throw new IllegalArgumentException("This metric was created with name '" + getKafkaName() +
          "' group '" + getKafkaGroup() + "' but update attempted with: " + name);
    }
    Object value = metric.metricValue();
    if (value == null) {
      throw new IllegalArgumentException("Null value for metric " + name);
    }
    if (value instanceof Double) {
      if (gauge == null) {
        setup(name);
      }
      List<String> labelValues = getLabelValues(name);
      gauge.labels(labelValues.toArray(new String[labelValues.size()])).set((Double) value);
    } else {
      if (lastUpdated == 0) {
        logger.warn("Ignoring metric {} with value type {}", value, value.getClass());
      }
    }
    lastUpdated = System.currentTimeMillis();
  }

  public List<String> getLabels() {
    return labels;
  }

  public long getLastUpdated() {
    return lastUpdated;
  }

}
