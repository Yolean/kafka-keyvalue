package se.yolean.kafka.keyvalue;

import javax.inject.Singleton;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import javax.inject.Inject;

/**
 * Relays the {@link #call()} because @Liveness and @Singleton
 * combined would break bean discovery on ConsumerAtLeastOnce.
 */
@Readiness // Consumer's call() is the essential check for cache being warm, i.e. not liveness.
@Singleton
public class ConsumerAtLeastOnceHealthProxy implements HealthCheck {

  @Inject
  ConsumerAtLeastOnce consumer;

  @Override
  public HealthCheckResponse call() {
    return consumer.call();
  }

}
