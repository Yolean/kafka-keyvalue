package se.yolean.kafka.keyvalue;

import javax.inject.Singleton;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import javax.inject.Inject;

/**
 * Relays the {@link #call()} because @Liveness and @Singleton
 * combined would break bean discovery on ConsumerAtLeastOnce.
 *
 * Also we want to handle the not-started-yet condition.
 */
@Readiness // Consumer's call() is the essential check for cache being warm, i.e. not liveness.
@Singleton
public class ConsumerAtLeastOnceHealthProxy implements HealthCheck {

  @Inject // Note that this can be null if cache is still in it's startup event handler
  ConsumerAtLeastOnce consumer;

  @Override
  public HealthCheckResponse call() {
    if (consumer == null) {
      return HealthCheckResponse.named("consume-loop").withData("stage", "NotStarted").build();
    }
    return consumer.call();
  }

}
