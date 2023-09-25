package se.yolean.kafka.keyvalue;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public class ConsumerAtLeastOnceTest {

  @Test
  void testToStats() {

    var registry = new SimpleMeterRegistry();
    var instance = new ConsumerAtLeastOnce(registry);
    assertFalse(registry.getMetersAsString().contains("17"), "Unexpected metrics: \n" + registry.getMetersAsString());
    instance.toStats(new UpdateRecord("mytopic", 0, 17, "key1", 100), false);
    assertTrue(registry.getMetersAsString().contains("17"), "Unexpected metrics: \n" + registry.getMetersAsString());
    instance.toStats(new UpdateRecord("mytopic", 0, 27, "key1", 100), false);
    assertFalse(registry.getMetersAsString().contains("17"), "Unexpected metrics: \n" + registry.getMetersAsString());
    assertFalse(registry.getMetersAsString().contains("NaN"), "Unexpected metrics: \n" + registry.getMetersAsString());
    assertTrue(registry.getMetersAsString().contains("27"), "Unexpected metrics: \n" + registry.getMetersAsString());
    instance.toStats(new UpdateRecord("mytopic", 0, 30, "key1", 100), false);
    assertTrue(registry.getMetersAsString().contains("30"), "Unexpected metrics: \n" + registry.getMetersAsString());
    instance.toStats(new UpdateRecord("mytopic", 0, 31, "key1", 100), false);
    assertTrue(registry.getMetersAsString().contains("31"), "Unexpected metrics: \n" + registry.getMetersAsString());
    instance.toStats(new UpdateRecord("mytopic", 0, 32, "key1", 100), false);
    assertTrue(registry.getMetersAsString().contains("32"), "Unexpected metrics: \n" + registry.getMetersAsString());
    instance.toStats(new UpdateRecord("mytopic", 0, 33, "key1", 100), false);
    assertTrue(registry.getMetersAsString().contains("33"));
  }


}
