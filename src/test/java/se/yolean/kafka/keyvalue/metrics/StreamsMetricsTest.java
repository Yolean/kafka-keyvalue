package se.yolean.kafka.keyvalue.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.MetricConfig;
import org.apache.kafka.common.metrics.Metrics;
import org.apache.kafka.common.metrics.Sensor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class StreamsMetricsTest {

  @Disabled // Mocking metrics didn't work
  @Test
  void testHasSeenAssignedParititions() {
    Map<String, String> metricTags = new HashMap<String, String>();
    MetricConfig metricConfig = new MetricConfig().tags(metricTags);
    Metrics metrics = new Metrics(metricConfig);

    Sensor sensor = metrics.sensor("assigned-partitions");
    MetricName metricName = metrics.metricName("consumer-coordinator-metrics", "assigned-partitions", "fake metric");
    sensor.add(metricName, new org.apache.kafka.common.metrics.stats.Value());
    sensor.record(1.0);

    assertEquals(1, metrics.metrics().size(), "This test setup should be a working mock");

    StreamsMetrics ourMetrics = new StreamsMetrics(metrics.metrics());
    assertTrue(ourMetrics.hasSeenAssignedParititions(), "Should have seen the assigned partitions value");

    metrics.close();
  }

}
