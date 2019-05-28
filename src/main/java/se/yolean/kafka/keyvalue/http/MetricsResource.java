package se.yolean.kafka.keyvalue.http;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.StreamingOutput;

import se.yolean.kafka.keyvalue.ConsumerAtLeastOnce;

@Path("/kafkametrics")
public class MetricsResource {

  @Inject
  ConsumerAtLeastOnce consumer;

  @GET
  public StreamingOutput prometheus() {
    throw new UnsupportedOperationException("Not implemented");
  }

}
