package se.yolean.kafka.keyvalue.onupdate.hc;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ResponseResultTest {

  @Test
  void testSomeUnrecognizedStatus() {
    ResponseResult result = new ResponseResult(333);
    assertFalse(result.isAck());
    assertEquals(333, result.getStatus());
    assertEquals("ResponseResult[status=333]", result.toString());
  }

}
