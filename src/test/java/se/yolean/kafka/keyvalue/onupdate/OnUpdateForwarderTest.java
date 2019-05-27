package se.yolean.kafka.keyvalue.onupdate;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class OnUpdateForwarderTest {

  @Test
  void testGetTargetsConfig() {
    OnUpdateForwarder forwarder = new OnUpdateForwarder();

    forwarder.target = Optional.empty();
    forwarder.target1 = Optional.empty();
    forwarder.target2 = Optional.empty();
    forwarder.target3 = Optional.empty();
    forwarder.target4 = Optional.empty();
    forwarder.target5 = Optional.empty();
    forwarder.target6 = Optional.empty();
    forwarder.target7 = Optional.empty();
    forwarder.target8 = Optional.empty();
    forwarder.target9 = Optional.empty();

    assertNotNull(forwarder.getTargetsConfig());
    assertEquals(0, forwarder.getTargetsConfig().size());

    forwarder.target = Optional.of("http://example.net/");
    assertEquals(1, forwarder.getTargetsConfig().size());
    assertEquals("http://example.net/", forwarder.getTargetsConfig().get(0));
  }

}
