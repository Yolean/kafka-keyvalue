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

package se.yolean.kafka.keyvalue.http;

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponse.State;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import se.yolean.kafka.keyvalue.KafkaCache;

class CacheResourceTest {

  @Test
  void testValueByKeyUnready() {
    CacheResource rest = new CacheResource();
    rest.cache = Mockito.mock(KafkaCache.class);
    Mockito.when(rest.cache.isReady()).thenReturn(false);
    try {
      rest.valueByKey("a", null);
      fail("Should have deined the request when cache isn't ready");
    } catch (javax.ws.rs.ServiceUnavailableException e) {
      assertEquals("Denied because cache is unready, check /health for status", e.getMessage());
    }
  }

  @Test
  void testKeysUnready() {
    CacheResource rest = new CacheResource();
    rest.cache = Mockito.mock(KafkaCache.class);
    Mockito.when(rest.cache.isReady()).thenReturn(false);
    try {
      rest.keys();
      fail("Should have deined the request when cache isn't ready");
    } catch (javax.ws.rs.ServiceUnavailableException e) {
      assertEquals("Denied because cache is unready, check /health for status", e.getMessage());
    }
  }

  @Test
  void testKeysJsonUnready() {
    CacheResource rest = new CacheResource();
    rest.cache = Mockito.mock(KafkaCache.class);
    Mockito.when(rest.cache.isReady()).thenReturn(false);
    try {
      rest.keys();
      fail("Should have deined the request when cache isn't ready");
    } catch (javax.ws.rs.ServiceUnavailableException e) {
      assertEquals("Denied because cache is unready, check /health for status", e.getMessage());
    }
  }

  @Test
  void testValuesUnready() {
    CacheResource rest = new CacheResource();
    rest.cache = Mockito.mock(KafkaCache.class);
    Mockito.when(rest.cache.isReady()).thenReturn(false);
    try {
      rest.values();
      fail("Should have deined the request when cache isn't ready");
    } catch (javax.ws.rs.ServiceUnavailableException e) {
      assertEquals("Denied because cache is unready, check /health for status", e.getMessage());
    }
  }

  @Test
  void testGetCurrentOffsetUnreadyAllowed() {
    CacheResource rest = new CacheResource();
    rest.cache = Mockito.mock(KafkaCache.class);
    Mockito.when(rest.cache.isReady()).thenReturn(false);
    rest.getCurrentOffset("t", 5);
  }

  @Test
  void testLivenessRegardlessOfCacheHealth() {
    CacheResource rest = new CacheResource();
    rest.cache = Mockito.mock(KafkaCache.class);
    HealthCheckResponse health = rest.call();
    assertTrue(health.getState().equals(State.UP), "Liveness should be true so we don't get killed during startup");
  }

  @Test
  void testLivenessWhenCacheIsNull() {
    CacheResource rest = new CacheResource();
    rest.cache = null; // Quarkus is allowed to do this. It's reasonable when cache is still in the StartupEvent handler
    HealthCheckResponse health = rest.call();
    assertTrue(health.getState().equals(State.UP), "Liveness should be true even when cache isn't initialized");
  }

}
