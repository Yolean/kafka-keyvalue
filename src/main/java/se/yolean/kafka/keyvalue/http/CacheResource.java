package se.yolean.kafka.keyvalue.http;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import se.yolean.kafka.keyvalue.ConsumerAtLeastOnce;

@Path("/kafka-keyvalue")
public class CacheResource {

  @Inject
  ConsumerAtLeastOnce consumer;

  @GET
  @Produces(MediaType.TEXT_PLAIN)
  public String hello() {
    return "hello" + consumer;
  }

}
