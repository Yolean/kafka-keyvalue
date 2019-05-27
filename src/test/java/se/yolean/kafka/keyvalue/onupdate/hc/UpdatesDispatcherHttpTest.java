package se.yolean.kafka.keyvalue.onupdate.hc;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UpdatesDispatcherHttpTest {

  @Test
  void testToString() {
    String configuredTarget = "http://some.host/__TOPIC__";
    UpdatesDispatcherHttp dispatcher = new UpdatesDispatcherHttp(configuredTarget );
    assertEquals("UpdatesDispatcherHttp[http://some.host/__TOPIC__]", dispatcher.toString());
  }

}
