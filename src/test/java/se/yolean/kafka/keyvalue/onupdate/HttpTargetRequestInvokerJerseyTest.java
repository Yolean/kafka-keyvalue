package se.yolean.kafka.keyvalue.onupdate;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HttpTargetRequestInvokerJerseyTest {

  @Test
  void testToString() {
    HttpTargetRequestInvokerJersey invoker = new HttpTargetRequestInvokerJersey("http://example.com", 12345, 4321);
    assertEquals("12345,4321,http://example.com", invoker.toString());
  }

}
