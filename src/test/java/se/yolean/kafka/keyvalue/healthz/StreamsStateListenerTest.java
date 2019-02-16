package se.yolean.kafka.keyvalue.healthz;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.kafka.streams.KafkaStreams.State;
import org.junit.jupiter.api.Test;

class StreamsStateListenerTest {

  /**
   * Based on observations using logging with Streams 2.1.0
   */
  @Test
  void testStatesAtStartupWithNonexistentTopic() {
    StreamsStateListener listener = new StreamsStateListener();
    // happens right after start
    listener.onChange(State.RUNNING, State.CREATED);
    listener.onChange(State.REBALANCING, State.RUNNING);
    // what sucks is that we never get these state transitions
    // INFO org.apache.kafka.streams.processor.internals.StreamThread - stream-thread [-StreamThread-1] State transition from PARTITIONS_REVOKED to PENDING_SHUTDOWN
    // INFO org.apache.kafka.streams.processor.internals.StreamThread - stream-thread [-StreamThread-1] State transition from PENDING_SHUTDOWN to DEAD
    // and .state() makes no difference, it also reports REBALANCING
    assertFalse(listener.streamsHasBeenRunning());
  }

  /**
   * Based on observations using logging with Streams 2.1.0
   */
  @Test
  void testStatesAtStartupWithTopic() {
    StreamsStateListener listener = new StreamsStateListener();
    listener.onChange(State.RUNNING, State.CREATED);
    listener.onChange(State.REBALANCING, State.RUNNING);
    listener.onChange(State.RUNNING, State.REBALANCING);
    assertTrue(listener.streamsHasBeenRunning());
  }

}
