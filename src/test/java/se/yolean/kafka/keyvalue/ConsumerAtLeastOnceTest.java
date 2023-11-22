package se.yolean.kafka.keyvalue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
  void testOffsetMetric() {
    var registry = new SimpleMeterRegistry();
    var instance = new ConsumerAtLeastOnce();
    instance.registry = registry;
    instance.cache = new HashMap<>();
    var onupdate = mock(OnUpdate.class);
    instance.onupdate = onupdate;

    instance.currentOffsets.put(new TopicPartition("mytopic", 0), new AtomicLong(-1L));
    instance.registerCurrentOffsetMetrics();
    assertEquals(1, registry.getMeters().size());
    assertEquals("kkv.last.seen.offset", registry.getMeters().get(0).getId().getName());

    var lastSeenOffsetCounter = registry.find("kkv.last.seen.offset").gauge();
    assertEquals(-1, lastSeenOffsetCounter.value());

    instance.cacheRecord(new ConsumerRecord<String,byte[]>("mytopic", 0, 17, "key1", new byte[]{}));
    assertEquals(17, lastSeenOffsetCounter.value());
    instance.cacheRecord(new ConsumerRecord<String,byte[]>("mytopic", 0, 27, "key1", new byte[]{}));
    assertEquals(27, lastSeenOffsetCounter.value());
    instance.cacheRecord(new ConsumerRecord<String,byte[]>("mytopic", 0, 30, "key1", new byte[]{}));
    assertEquals(30, lastSeenOffsetCounter.value());
    instance.cacheRecord(new ConsumerRecord<String,byte[]>("mytopic", 0, 31, "key1", new byte[]{}));
    assertEquals(31, lastSeenOffsetCounter.value());
    instance.cacheRecord(new ConsumerRecord<String,byte[]>("mytopic", 0, 32, "key1", new byte[]{}));
    assertEquals(32, lastSeenOffsetCounter.value());
    instance.cacheRecord(new ConsumerRecord<String,byte[]>("mytopic", 0, 33, "key1", new byte[]{}));
    assertEquals(33, lastSeenOffsetCounter.value());
  }

  @Test
  void testGetCurrentOffset() {
    var registry = new SimpleMeterRegistry();
    var instance = new ConsumerAtLeastOnce();
    instance.registry = registry;

    assertDoesNotThrow(() -> instance.getCurrentOffset("some-topic", 0), () -> {
      return "getCurrentOffset should not throw when the requested topic-partition does not (yet) exist";
    });
    assertNull(instance.getCurrentOffset("some-topic", 0), () -> {
      return "getCurrentOffset should return null when the requested topic-partition does not (yet) exist";
    });
  }

  @Test
  void testValueEqual() {
    var registry = new SimpleMeterRegistry();
    var instance = new ConsumerAtLeastOnce();
    instance.registry = registry;

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
    var instance = new ConsumerAtLeastOnce();
    instance.registry = registry;

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
