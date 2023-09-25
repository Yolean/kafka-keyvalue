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
    assertFalse(registry.getMetersAsString().contains("17"));
    instance.toStats(new UpdateRecord("mytopic", 0, 17, "key1", 100), false);
    assertTrue(registry.getMetersAsString().contains("17"));
    instance.toStats(new UpdateRecord("mytopic", 0, 27, "key1", 100), false);
    assertFalse(registry.getMetersAsString().contains("17"));
    assertFalse(registry.getMetersAsString().contains("NaN"));
    assertTrue(registry.getMetersAsString().contains("27"));
    instance.toStats(new UpdateRecord("mytopic", 0, 30, "key1", 100), false);
    assertTrue(registry.getMetersAsString().contains("30"));
    instance.toStats(new UpdateRecord("mytopic", 0, 31, "key1", 100), false);
    assertTrue(registry.getMetersAsString().contains("31"));
    instance.toStats(new UpdateRecord("mytopic", 0, 32, "key1", 100), false);
    assertTrue(registry.getMetersAsString().contains("32"));
    instance.toStats(new UpdateRecord("mytopic", 0, 33, "key1", 100), false);
    assertTrue(registry.getMetersAsString().contains("33"));
  }


}
