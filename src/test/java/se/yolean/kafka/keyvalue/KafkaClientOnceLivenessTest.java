package se.yolean.kafka.keyvalue;

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.microprofile.health.HealthCheckResponse.State;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class KafkaClientOnceLivenessTest {

  /**
   * This is behavior we no longer want if we instead aim to recover from kafka client connection failures, or terminate programmatically
   */
  @Test
  void testCallAtAssigned() {
    KafkaClientOnceLiveness liveness = new KafkaClientOnceLiveness();
    liveness.consumer = Mockito.mock(ConsumerAtLeastOnce.class);

    assertEquals(true, liveness.call().getState().equals(State.UP),
        "Should report live until the opposite is proven");
    liveness.consumer.stage = ConsumerAtLeastOnce.Stage.Assigning;
    assertEquals(true, liveness.call().getState().equals(State.DOWN),
        "Might be ok to trigger non-liveness on the hopefully brief assigning phase");
    liveness.consumer.stage = ConsumerAtLeastOnce.Stage.Resetting;
    assertEquals(true, liveness.call().getState().equals(State.UP),
        "As soon as we're out of Assigning we should be live");
    liveness.consumer.stage = ConsumerAtLeastOnce.Stage.Assigning;
    assertEquals(true, liveness.call().getState().equals(State.UP),
        "From now on we should always be up");
  }

}
