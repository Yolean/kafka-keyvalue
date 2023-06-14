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
import static org.mockito.ArgumentMatchers.any;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponse.Status;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import se.yolean.kafka.keyvalue.KafkaCache;
import se.yolean.kafka.keyvalue.TopicPartitionOffset;

class CacheResourceTest {

  @Test
  void testValueByKeyUnready() throws JsonProcessingException {
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
  void testStreamValues() throws IOException {
    CacheResource rest = new CacheResource();
    rest.cache = Mockito.mock(KafkaCache.class);
    rest.mapper = new ObjectMapper();
    Mockito.when(rest.cache.isReady()).thenReturn(true);
    Mockito.when(rest.cache.getValues()).thenReturn(List.of("a".getBytes(), "b".getBytes()).iterator());
    assertEquals("a\nb\n", rest.values().getEntity().toString());
  }

  @Test
  void testValueEndpointWithOffsetHeaders() throws IOException {
    CacheResource rest = new CacheResource();
    rest.cache = Mockito.mock(KafkaCache.class);
    rest.mapper = new ObjectMapper();
    rest.mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    Mockito.when(rest.cache.isReady()).thenReturn(true);
    Mockito.when(rest.cache.getValues()).thenReturn(List.of("a".getBytes()).iterator());
    Mockito.when(rest.cache.getValue(any())).thenReturn("a".getBytes());
    assertEquals("a", new String(rest.cache.getValue("key1"), StandardCharsets.UTF_8));

    Mockito.when(rest.cache.getCurrentOffsets()).thenReturn(List.of(
        new TopicPartitionOffset("mytopic", 0, 0L)));

    assertEquals("[x-kkv-last-seen-offsets]", "" + rest.values().getHeaders().keySet());
    assertEquals("[{\"offset\":0,\"partition\":0,\"topic\":\"mytopic\"}]", rest.values().getHeaderString("x-kkv-last-seen-offsets"));
    assertEquals("[{\"offset\":0,\"partition\":0,\"topic\":\"mytopic\"}]", rest.valueByKey("key1", null).getHeaderString("x-kkv-last-seen-offsets"));

    Mockito.when(rest.cache.getCurrentOffsets()).thenReturn(List.of(
        new TopicPartitionOffset("mytopic", 0, 17045L)));

    assertEquals("[{\"offset\":17045,\"partition\":0,\"topic\":\"mytopic\"}]", rest.values().getHeaderString("x-kkv-last-seen-offsets"));
    assertEquals("[{\"offset\":17045,\"partition\":0,\"topic\":\"mytopic\"}]", rest.valueByKey("key1", null).getHeaderString("x-kkv-last-seen-offsets"));
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
  void testValuesUnready() throws IOException {
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
    assertTrue(health.getStatus().equals(Status.UP), "Liveness should be true so we don't get killed during startup");
  }

  @Test
  void testLivenessWhenCacheIsNull() {
    CacheResource rest = new CacheResource();
    rest.cache = null; // Quarkus is allowed to do this. It's reasonable when cache is still in the StartupEvent handler
    HealthCheckResponse health = rest.call();
    assertTrue(health.getStatus().equals(Status.UP), "Liveness should be true even when cache isn't initialized");
  }

}
