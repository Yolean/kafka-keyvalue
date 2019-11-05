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

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponse.State;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ConsumerAtLeastOnceHealthProxyTest {

  @Test
  void testCall() {
    ConsumerAtLeastOnceHealthProxy healthProxy = new ConsumerAtLeastOnceHealthProxy();
    healthProxy.consumer = Mockito.mock(ConsumerAtLeastOnce.class);
    HealthCheckResponse health = HealthCheckResponse.builder().name("test").build();
    Mockito.when(healthProxy.consumer.call()).thenReturn(health);
    assertEquals(health, healthProxy.call(), "Should return the health result as-is");
  }

  @Test
  void testCallWhenNotInitialized() {
    ConsumerAtLeastOnceHealthProxy healthProxy = new ConsumerAtLeastOnceHealthProxy();
    healthProxy.consumer = null; // Quarkus is allowed to do this. It's reasonable when cache is still in the StartupEvent handler
    HealthCheckResponse health = healthProxy.call();
    assertEquals(true, health.getState().equals(State.DOWN), "Should report unready when not initialized");
  }

}
