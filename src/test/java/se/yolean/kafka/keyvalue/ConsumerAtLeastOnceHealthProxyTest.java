package se.yolean.kafka.keyvalue;

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponse.State;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ConsumerAtLeastOnceHealthProxyTest {

  @Test
  void testCall() {
    ConsumerAtLeastOnceHealthProxy healthProxy = new ConsumerAtLeastOnceHealthProxy();
    healthProxy.consumer = Mockito.mock(ConsumerAtLeastOnce.class);
    HealthCheckResponse health = HealthCheckResponse.builder().name("test").build();
    Mockito.when(healthProxy.consumer.call()).thenReturn(health);
    assertEquals(health, healthProxy.call(), "Should return the health result as-is");
  }

  @Test
  void testCallWhenNotInitialized() {
    ConsumerAtLeastOnceHealthProxy healthProxy = new ConsumerAtLeastOnceHealthProxy();
    healthProxy.consumer = null; // Quarkus is allowed to do this. It's reasonable when cache is still in the StartupEvent handler
    HealthCheckResponse health = healthProxy.call();
    assertEquals(true, health.getState().equals(State.DOWN), "Should report unready when not initialized");
  }

}
