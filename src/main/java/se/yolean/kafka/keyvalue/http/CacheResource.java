package se.yolean.kafka.keyvalue.http;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.eclipse.microprofile.health.Health;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;

import se.yolean.kafka.keyvalue.ConsumerAtLeastOnce;

@Health
@Path("/kafka-keyvalue")
public class CacheResource implements HealthCheck {

  @Inject
  ConsumerAtLeastOnce consumer;

  @Override
  public HealthCheckResponse call() {
    return consumer.call();
  }

  @GET
  @Produces(MediaType.TEXT_PLAIN)
  public String hello() {
    return "hello" + consumer;
  }

}
