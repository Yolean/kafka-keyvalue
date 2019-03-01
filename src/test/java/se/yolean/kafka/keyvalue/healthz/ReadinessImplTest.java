package se.yolean.kafka.keyvalue.healthz;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import se.yolean.kafka.keyvalue.KafkaCache;
import se.yolean.kafka.keyvalue.metrics.StreamsMetrics;

class ReadinessImplTest {

  @Test
  void test() {
    KafkaCache cache = Mockito.mock(KafkaCache.class);
    StreamsStateListener state = Mockito.mock(StreamsStateListener.class);
    StreamsMetrics metrics = Mockito.mock(StreamsMetrics.class);
    ReadinessImpl readiness = new ReadinessImpl(cache, state, metrics);
    assertFalse(readiness.isAppReady());
    Mockito.when(cache.isReady()).thenReturn(true);
    assertFalse(readiness.isAppReady());
    Mockito.when(state.streamsHasBeenRunning()).thenReturn(true);
    assertFalse(readiness.isAppReady());
    Mockito.when(metrics.hasSeenAssignedParititions()).thenReturn(true);
    assertTrue(readiness.isAppReady(), "Three readiness criterias ok, and that's all for now");
  }

}
