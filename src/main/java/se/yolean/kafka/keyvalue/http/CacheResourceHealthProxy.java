package se.yolean.kafka.keyvalue.http;

import javax.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

@Liveness
public class CacheResourceHealthProxy implements HealthCheck {

  private CacheResource resource;

  @Inject
  public CacheResourceHealthProxy(CacheResource resource) {
    this.resource = resource;
  }

  @Override
  public HealthCheckResponse call() {
    if (this.resource == null) {
      return HealthCheckResponse.builder()
          .name("REST resource existence, not really endpoint liveness")
          .down().build();
    }
    // This is also not liveness.
    // Maybe Microprofile Health 2.0 is an abstraction too far from liveness, though good for readiness.
    return this.resource.call();
  }

}
