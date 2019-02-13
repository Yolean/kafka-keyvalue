package se.yolean.kafka.keyvalue;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class KeyvalueUpdateProcessorTest {

  @Test
  void testGetValueNullKey() {
    KeyvalueUpdateProcessor cache = new KeyvalueUpdateProcessor("t", new OnUpdateRecordInMemory());
    try {
      cache.getValue(null);
      fail("Should throw on null key");
    } catch (IllegalArgumentException e) {
      assertEquals("Key can not be null because such messages are ignored at cache update", e.getMessage());
    }
  }

  @Test
  void testGetValueKeyLengthZero() {
    KeyvalueUpdateProcessor cache = new KeyvalueUpdateProcessor("t", new OnUpdateRecordInMemory());
    try {
      cache.getValue(null);
      fail("Should throw on empty key");
    } catch (IllegalArgumentException e) {
      assertEquals("Key can not be null because such messages are ignored at cache update", e.getMessage());
    }
  }

}
