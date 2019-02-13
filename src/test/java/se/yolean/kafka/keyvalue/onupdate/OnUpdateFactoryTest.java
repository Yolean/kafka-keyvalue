package se.yolean.kafka.keyvalue.onupdate;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OnUpdateFactoryTest {

  @Test
  void testFromUrlNoProtocol() {
    OnUpdateFactory factory = OnUpdateFactory.getInstance();
    try {
      factory.fromUrl("null/myupdates/");
      fail("Should have rejected invalid URL");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("null/myupdates/"));
    }
  }

}
