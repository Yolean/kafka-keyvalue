package se.yolean.kafka.keyvalue.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.prometheus.client.Gauge;

public class KafkaGaugeToPrometheus {

  private static final Logger logger = LoggerFactory.getLogger(KafkaGaugeToPrometheus.class);

  private String kafkaName;
  private String kafkaGroup;
  private String nameInPrometheus;

  private Gauge gauge = null;

  private List<String> labels = null;

  private long lastUpdated = 0;

  static String getNameInPrometheus(MetricName kafkaMetricName) {
    return getNameInPrometheus(kafkaMetricName.group(), kafkaMetricName.name());
  }

  static String getNameInPrometheus(String kafkaGroup, String kafkaName) {
    return kafkaGroup.replace('-', '_') + "__" + kafkaName.replace('-', '_');
  }

  public KafkaGaugeToPrometheus(String kafkaGroup, String kafkaName) {
    if (kafkaName == null) {
      throw new IllegalArgumentException("Metric name can not be null");
    }
    if (kafkaGroup == null) {
      throw new IllegalArgumentException("Metric group can not be null");
    }
    this.kafkaName = kafkaName;
    this.kafkaGroup = kafkaGroup;
    this.nameInPrometheus = getNameInPrometheus(kafkaGroup, kafkaName);
  }

  public KafkaGaugeToPrometheus(MetricName metricName) {
    this(metricName.group(), metricName.name());
  }

  public String getNameInPrometheus() {
    return nameInPrometheus;
  }

  @Override
  public String toString() {
    return getNameInPrometheus();
  }

  public String getKafkaName() {
    return kafkaName;
  }

  public String getKafkaGroup() {
    return kafkaGroup;
  }

  private void setup(MetricName name) {
    List<String> labels = getLabelNames(name);
    if ("consumer-fetch-manager-metrics".equals(name.group()) && !labels.contains("topic")) {
      logger.info("Metric {} will not be registered until we have a sample that contains topic name", name);
      return;
    }
    String description = name.description();
    if (description == null || description.length() == 0) {
      description = "No description provided by Kafka";
    }
    gauge = Gauge.build()
        .name(nameInPrometheus)
        .labelNames(labels.toArray(new String[labels.size()]))
        .help(description)
        .register();
    this.labels = Collections.unmodifiableList(labels);
    logger.debug("New prometheus metric created: {} from {}", this, name);
  }

  private List<String> getLabelNames(MetricName name) {
    ArrayList<String> labels = new ArrayList<>(name.tags().size());
    name.tags().keySet().forEach(key -> labels.add(key.replace('-', '_')));
    return labels;
  }

  private List<String> getLabelValues(MetricName name) {
    ArrayList<String> labels = new ArrayList<>(name.tags().size());
    name.tags().values().forEach(value -> labels.add(value));
    return labels;
  }

  public void update(Metric metric) {
    String promName = getNameInPrometheus(metric.metricName());
    if (!getNameInPrometheus().equals(promName)) {
      throw new IllegalArgumentException("This metric was created with name '" + getKafkaName() +
          "' group '" + getKafkaGroup() + "' but update attempted with: " + metric.metricName());
    }
    Object value = metric.metricValue();
    if (value == null) {
      throw new IllegalArgumentException("Null value for metric " + metric.metricName());
    }
    if (value instanceof Double) {
      if (gauge == null) {
        setup(metric.metricName());
      }
      List<String> labelValues = getLabelValues(metric.metricName());
      try {
        gauge.labels(labelValues.toArray(new String[labelValues.size()])).set((Double) value);
      } catch (IllegalArgumentException e) {
        if ("Incorrect number of labels.".equals(e.getMessage())) {
          logger.error("Failed to update metric {} with labels {}, different number of labels than: {}",
              promName, this.labels, metric.metricName());
        } else {
          throw e;
        }
      }
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
