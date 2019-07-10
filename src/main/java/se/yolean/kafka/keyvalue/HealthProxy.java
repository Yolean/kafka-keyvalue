package se.yolean.kafka.keyvalue;

import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;

import javax.inject.Inject;

/**
 * Health annotations on rest or consumer messed with dependency injection,
 * hence this proxy.
 */
@Liveness
public class HealthProxy implements HealthCheck {

  @Inject
  ConsumerAtLeastOnce consumer;

  @Override
  public HealthCheckResponse call() {
    return consumer.call();
  }

}
