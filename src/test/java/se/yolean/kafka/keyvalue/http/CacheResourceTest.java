package se.yolean.kafka.keyvalue.http;

import static org.junit.jupiter.api.Assertions.*;

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

}
