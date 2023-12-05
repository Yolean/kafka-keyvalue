// Copyright 2019 Yolean AB
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package se.yolean.kafka.keyvalue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import org.eclipse.microprofile.health.HealthCheckResponse.Status;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class KafkaClientOnceLivenessTest {

  /**
   * This is behavior we no longer want if we instead aim to recover from kafka client connection failures, or terminate programmatically
   */
  @Test
  void testCallAtAssigned() {
    KafkaClientOnceLiveness liveness = new KafkaClientOnceLiveness();
    ConsumerAtLeastOnce consumer = Mockito.mock(ConsumerAtLeastOnce.class);
    liveness.consumer = consumer;

    assertEquals(true, liveness.call().getStatus().equals(Status.UP),
        "Should report live until the opposite is proven");
    when(consumer.getStage()).thenReturn(KafkaCache.Stage.Assigning);
    assertEquals(true, liveness.call().getStatus().equals(Status.DOWN),
        "Might be ok to trigger non-liveness on the hopefully brief assigning phase");
    when(consumer.getStage()).thenReturn(KafkaCache.Stage.Resetting);
    assertEquals(true, liveness.call().getStatus().equals(Status.UP),
        "As soon as we're out of Assigning we should be live");
    when(consumer.getStage()).thenReturn(KafkaCache.Stage.Assigning);
    assertEquals(true, liveness.call().getStatus().equals(Status.UP),
        "From now on we should always be up");
  }

}
