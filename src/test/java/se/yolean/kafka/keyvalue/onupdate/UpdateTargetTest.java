package se.yolean.kafka.keyvalue.onupdate;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UpdateTargetTest {

  @Test
  void test() {
    UpdateTarget updateTarget = new UpdateTarget("http://myhost.local:12345/whatever/uri?and-params=true");
    assertEquals("http://myhost.local:12345", updateTarget.getHttpclientContextHost().toString());
    assertEquals("/whatever/uri?and-params=true", updateTarget.getHttpUriFromHost().toString());
  }

}
