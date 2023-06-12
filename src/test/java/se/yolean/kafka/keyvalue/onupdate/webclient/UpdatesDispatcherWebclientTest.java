package se.yolean.kafka.keyvalue.onupdate.webclient;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public class UpdatesDispatcherWebclientTest {


  @Test
  void testTargetUpdateFailureMetric() {
    var registry = new SimpleMeterRegistry();

    assertEquals("", registry.getMetersAsString());
    UpdatesDispatcherWebclient.initMetrics(registry);
    assertEquals("kkv.target.update.failure(COUNTER)[]; count=0.0", registry.getMetersAsString());
  }
}
