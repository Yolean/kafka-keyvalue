package se.yolean.kafka.keyvalue.onupdate;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UpdateTargetTest {

  @Test
  void test() {
    UpdateTarget updateTarget = new UpdateTarget("http://myhost.local:12345/whatever/uri?and-params=true");
    assertEquals("http://myhost.local:12345", updateTarget.getHttpclientContextHost().toString());
    assertEquals("/whatever/uri?and-params=true", updateTarget.getHttpUriFromHost("t1").toString());
  }

  @Test
  void testWithTopicNameInPath() {
    UpdateTarget updateTarget = new UpdateTarget("http://dev.local:123/updates/__TOPIC__/");
    assertEquals("http://dev.local:123", updateTarget.getHttpclientContextHost().toString());
    assertEquals("/updates/topic1/", updateTarget.getHttpUriFromHost("topic1").toString());
  }

  @Test
  void testWithTopicNameInQuery() {
    UpdateTarget updateTarget = new UpdateTarget("http://dev.local:123/updates?topic=__TOPIC__");
    assertEquals("http://dev.local:123", updateTarget.getHttpclientContextHost().toString());
    assertEquals("/updates?topic=Topic2", updateTarget.getHttpUriFromHost("Topic2").toString());
  }

}
