package se.yolean.kafka.keyvalue;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public class ConsumerAtLeastOnceTest {

  @Test
  void testToStats() {

    var registry = new SimpleMeterRegistry();
    var instance = new ConsumerAtLeastOnce(registry);
    // TODO as we add more metrics these assertions must extract the value of an actual metric name
    assertFalse(registry.getMetersAsString().contains("17"), "Unexpected metrics: \n" + registry.getMetersAsString());
    instance.toStats(new UpdateRecord("mytopic", 0, 17, "key1", 100), false);
    assertTrue(registry.getMetersAsString().contains("17"), "Unexpected metrics: \n" + registry.getMetersAsString());
    instance.toStats(new UpdateRecord("mytopic", 0, 27, "key1", 100), false);
    assertFalse(registry.getMetersAsString().contains("17"), "Unexpected metrics: \n" + registry.getMetersAsString());
    assertTrue(registry.getMetersAsString().contains("27"), "Unexpected metrics: \n" + registry.getMetersAsString());
    instance.toStats(new UpdateRecord("mytopic", 0, 30, "key1", 100), false);
    assertTrue(registry.getMetersAsString().contains("30"), "Unexpected metrics: \n" + registry.getMetersAsString());
    instance.toStats(new UpdateRecord("mytopic", 0, 31, "key1", 100), false);
    assertTrue(registry.getMetersAsString().contains("31"), "Unexpected metrics: \n" + registry.getMetersAsString());
    instance.toStats(new UpdateRecord("mytopic", 0, 32, "key1", 100), false);
    assertTrue(registry.getMetersAsString().contains("32"), "Unexpected metrics: \n" + registry.getMetersAsString());
    instance.toStats(new UpdateRecord("mytopic", 0, 33, "key1", 100), false);
    assertTrue(registry.getMetersAsString().contains("33"), "Unexpected metrics: \n" + registry.getMetersAsString());
  }

  @Test
  void testValueEqual() {
    var registry = new SimpleMeterRegistry();
    var instance = new ConsumerAtLeastOnce(registry);

    instance.cache = new HashMap<>();
    instance.cache.put("k1", "v1".getBytes());

    assertFalse(instance.isValueEqual("k1", "v0".getBytes()));
    assertTrue(instance.isValueEqual("k1", "v1".getBytes()));
    assertFalse(instance.isValueEqual("k1", "v1-".getBytes()));
    assertFalse(instance.isValueEqual("k1", "-v1".getBytes()));
    assertFalse(instance.isValueEqual("k1", "v".getBytes()));
    assertFalse(instance.isValueEqual("k1", "1".getBytes()));
    assertFalse(instance.isValueEqual("k1", "".getBytes()));
    assertFalse(instance.isValueEqual("k1-", "v1".getBytes()));
  }

  void mockPollEnd(KafkaPollListener listener) {
    mockPollEnd(listener, 1);
  }

  void mockPollEnd(KafkaPollListener listener, int recordsCount) {
    @SuppressWarnings("unchecked")
    ConsumerRecords<String, byte[]> records = mock(ConsumerRecords.class);
    when(records.count()).thenReturn(recordsCount);
    listener.onConsume(records);
  }

  @Test
  void testValueDuplicate() {
    var registry = new SimpleMeterRegistry();
    var instance = new ConsumerAtLeastOnce(registry);

    KafkaPollListener listener = new KafkaPollListener();

    instance.onupdate = mock(OnUpdate.class);

    TopicPartition tp = new TopicPartition("t1", 0);
    Collection<TopicPartition> tps = Collections.singleton(tp);

    Consumer<?, ?> consumer = mock(Consumer.class);
    when(consumer.beginningOffsets(eq(tps), any())).thenReturn(Map.of(tp, 0L));
    when(consumer.position(tp)).thenReturn(1L);
    instance.onPartitionsAssigned(consumer, tps);

    instance.cache = new HashMap<>();
    instance.cache.put("k1", "v1".getBytes());
    instance.currentOffsets.put(tp, new AtomicLong(0));
    instance.consume(new ConsumerRecord<>(tp.topic(), tp.partition(), 1, "k1", "v1".getBytes()));
    mockPollEnd(listener);
    verify(instance.onupdate, times(0)).handle(any());
    assertTrue(registry.getMetersAsString().contains("kkv.onupdate.suppressed(COUNTER)[partition='0', suppress_reason='value_deduplication', topic='t1']; count=1.0"),
        "Unexpected metrics: \n" + registry.getMetersAsString());

    instance.consume(new ConsumerRecord<>(tp.topic(), tp.partition(), 1, "k1", "v2".getBytes()));
    mockPollEnd(listener);
    verify(instance.onupdate, times(1)).handle(any());
    assertTrue(registry.getMetersAsString().contains("kkv.onupdate.suppressed(COUNTER)[partition='0', suppress_reason='value_deduplication', topic='t1']; count=1.0"),
        "Unexpected metrics: \n" + registry.getMetersAsString());

    instance.consume(new ConsumerRecord<>(tp.topic(), tp.partition(), 1, "k1", "v2".getBytes()));
    mockPollEnd(listener);
    verify(instance.onupdate, times(1)).handle(any());
    assertTrue(registry.getMetersAsString().contains("kkv.onupdate.suppressed(COUNTER)[partition='0', suppress_reason='value_deduplication', topic='t1']; count=2.0"),
        "Unexpected metrics: \n" + registry.getMetersAsString());

    instance.consume(new ConsumerRecord<>(tp.topic(), tp.partition(), 1, "k1", "v3".getBytes()));
    mockPollEnd(listener);
    verify(instance.onupdate, times(2)).handle(any());
  }

}
