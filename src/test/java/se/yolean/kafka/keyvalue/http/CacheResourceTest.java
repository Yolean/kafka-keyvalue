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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import se.yolean.kafka.keyvalue.ConsumeLoopReadiness;
import se.yolean.kafka.keyvalue.KafkaCache;
import se.yolean.kafka.keyvalue.TopicPartitionOffset;

class CacheResourceTest {

  @Test
  void testValueByKeyUnready() throws JsonProcessingException {
    CacheResource rest = new CacheResource();
    rest.cacheReadiness = Mockito.mock(ConsumeLoopReadiness.class);
    when(rest.cacheReadiness.call()).thenReturn(HealthCheckResponse.down("consume-loop"));
    try {
      rest.valueByKey("a", null);
      fail("Should have deined the request when cache isn't ready");
    } catch (jakarta.ws.rs.ServiceUnavailableException e) {
      assertEquals("Denied because cache is unready, check /health for status", e.getMessage());
    }
  }

  @Test
  void testStreamValues() throws IOException {
    CacheResource rest = new CacheResource();
    rest.cache = Mockito.mock(KafkaCache.class);
    rest.mapper = new ObjectMapper();
    rest.cacheReadiness = Mockito.mock(ConsumeLoopReadiness.class);
    when(rest.cacheReadiness.call()).thenReturn(HealthCheckResponse.up("consume-loop"));
    final Iterator<byte[]> values = List.of("a".getBytes(), "b".getBytes()).iterator();
    Mockito.when(rest.cache.getValues()).thenReturn(values);

    assertTrue(rest.values().getEntity() == values);
  }

  @Test
  void testValueEndpointWithOffsetHeaders() throws IOException {
    CacheResource rest = new CacheResource();
    rest.cache = Mockito.mock(KafkaCache.class);
    rest.mapper = new ObjectMapper();
    rest.mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    rest.cacheReadiness = Mockito.mock(ConsumeLoopReadiness.class);
    when(rest.cacheReadiness.call()).thenReturn(HealthCheckResponse.up("consume-loop"));
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
  void testKeysUnready() throws IOException {
    CacheResource rest = new CacheResource();
    rest.cache = Mockito.mock(KafkaCache.class);
    rest.cacheReadiness = Mockito.mock(ConsumeLoopReadiness.class);
    when(rest.cacheReadiness.call()).thenReturn(HealthCheckResponse.down("consume-loop"));
    try {
      rest.keys();
      fail("Should have deined the request when cache isn't ready");
    } catch (jakarta.ws.rs.ServiceUnavailableException e) {
      assertEquals("Denied because cache is unready, check /health for status", e.getMessage());
    }
  }

  @Test
  void testKeysJsonUnready() throws IOException {
    CacheResource rest = new CacheResource();
    rest.cache = Mockito.mock(KafkaCache.class);
    rest.cacheReadiness = Mockito.mock(ConsumeLoopReadiness.class);
    when(rest.cacheReadiness.call()).thenReturn(HealthCheckResponse.down("consume-loop"));
    try {
      rest.keys();
      fail("Should have deined the request when cache isn't ready");
    } catch (jakarta.ws.rs.ServiceUnavailableException e) {
      assertEquals("Denied because cache is unready, check /health for status", e.getMessage());
    }
  }

  @Test
  void testValuesUnready() throws IOException {
    CacheResource rest = new CacheResource();
    rest.cache = Mockito.mock(KafkaCache.class);
    rest.cacheReadiness = Mockito.mock(ConsumeLoopReadiness.class);
    when(rest.cacheReadiness.call()).thenReturn(HealthCheckResponse.down("consume-loop"));
    try {
      rest.values();
      fail("Should have deined the request when cache isn't ready");
    } catch (jakarta.ws.rs.ServiceUnavailableException e) {
      assertEquals("Denied because cache is unready, check /health for status", e.getMessage());
    }
  }

  @Test
  void testGetCurrentOffsetUnreadyAllowed() {
    CacheResource rest = new CacheResource();
    rest.cache = Mockito.mock(KafkaCache.class);
    rest.cacheReadiness = Mockito.mock(ConsumeLoopReadiness.class);
    when(rest.cacheReadiness.call()).thenReturn(HealthCheckResponse.down("consume-loop"));
    rest.getCurrentOffset("t", 5);
  }

}
