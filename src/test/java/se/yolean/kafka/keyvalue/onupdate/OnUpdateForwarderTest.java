package se.yolean.kafka.keyvalue.onupdate;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class OnUpdateForwarderTest {

  @Test
  void testGetTargetsConfig() {
    OnUpdateForwarder forwarder = new OnUpdateForwarder();

    forwarder.targetsConfig = Optional.empty();
    assertNotNull(forwarder.getTargetsConfig());
    assertEquals(0, forwarder.getTargetsConfig().size());

    forwarder.targetsConfig = Optional.of("http://example.net/");
    assertEquals(1, forwarder.getTargetsConfig().size());
    assertEquals("http://example.net/", forwarder.getTargetsConfig().get(0));
  }

}
