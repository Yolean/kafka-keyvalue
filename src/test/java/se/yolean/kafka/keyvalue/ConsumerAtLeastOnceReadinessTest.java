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

import java.util.Map;

import org.apache.kafka.common.TopicPartition;
import org.eclipse.microprofile.health.HealthCheckResponse.Status;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import se.yolean.kafka.keyvalue.KafkaCache.Stage;

class ConsumerAtLeastOnceReadinessTest {

  @Test
  void testReadiness() {
    ConsumeLoopReadiness readiness = new ConsumeLoopReadiness();
    ConsumerAtLeastOnce consumer = Mockito.mock(ConsumerAtLeastOnce.class);
    readiness.consumer = consumer;
    when(consumer.isEndOffsetsReached()).thenCallRealMethod();

    when(consumer.getStage()).thenReturn(Stage.Polling);
    consumer.endOffsets = Map.of(
      new TopicPartition("topic", 0), 0L,
      new TopicPartition("topic", 1), 1L,
      new TopicPartition("topic", 2), 2L
    );

    when(consumer.getCurrentOffset("topic", 0)).thenReturn(0L);
    when(consumer.getCurrentOffset("topic", 1)).thenReturn(0L);
    when(consumer.getCurrentOffset("topic", 2)).thenReturn(0L);
    assertEquals(Status.DOWN, readiness.call().getStatus(), "It should be unready when no endOffset is reached");

    when(consumer.getCurrentOffset("topic", 0)).thenReturn(10L);
    when(consumer.getCurrentOffset("topic", 1)).thenReturn(10L);
    when(consumer.getCurrentOffset("topic", 2)).thenReturn(0L);
    assertEquals(Status.DOWN, readiness.call().getStatus(), "It should be unready when some endOffset is not reached");

    when(consumer.getCurrentOffset("topic", 0)).thenReturn(0L);
    when(consumer.getCurrentOffset("topic", 1)).thenReturn(1L);
    when(consumer.getCurrentOffset("topic", 2)).thenReturn(2L);
    assertEquals(Status.UP, readiness.call().getStatus(), "It should be ready when all endOffsets are reached");

    when(consumer.getCurrentOffset("topic", 0)).thenReturn(1L);
    when(consumer.getCurrentOffset("topic", 1)).thenReturn(2L);
    when(consumer.getCurrentOffset("topic", 2)).thenReturn(3L);
    assertEquals(Status.UP, readiness.call().getStatus(), "It should be ready when all endOffsets are reached");

    when(consumer.getStage()).thenReturn(Stage.PollingHistorical);
    assertEquals(Status.DOWN, readiness.call().getStatus(), "It should be unready stage is less than \"Polling\"");
  }

}
