package se.yolean.kafka.keyvalue.metrics;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class StreamsMetricsTest {

  @Test
  void testHasSeenAssignedParititions() {
    Map<MetricName, ? extends Metric> metrics = new HashMap<>();
    //MetricName assigned = mock(MetricName.class);
    //when(assigned.name()).thenReturn("");
    //when(assigned.group()).thenReturn("");
    //metrics.put(
    // ... argh, don't mock other people's code
    Metric assignedMetric = mock(Metric.class);
    when(assignedMetric.metricValue()).thenReturn(1.0).thenReturn(0.0);

    StreamsMetrics ourMetrics = new StreamsMetrics(metrics);
    //assertTrue(ourMetrics.hasSeenAssignedParititions(), "Should have seen the assigned partitions value");
  }

}
