package se.yolean.kafka.keyvalue.http;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import se.yolean.kafka.keyvalue.ConsumerAtLeastOnce;

@Path("/ready")
public class ReadinessResource {

  public static final String READINESS_CONTENT_TYPE = "application/json";
  public static final byte[] READY_JSON = "{\"ready\":true}".getBytes();
  public static final byte[] UNREADY_JSON = "{\"ready\":false}".getBytes();
  public static final byte[] NULL_JSON = "{\"ready\":undefined}".getBytes();

  @Inject
  ConsumerAtLeastOnce consumer;

  @GET
  public Response ready() {
    if (consumer == null) {
      return Response.status(503).type(READINESS_CONTENT_TYPE).entity(NULL_JSON).build();
    }
    if (consumer.isReady()) {
      return Response.ok(READY_JSON, READINESS_CONTENT_TYPE).build();
    }
    return Response.status(503).type(READINESS_CONTENT_TYPE).entity(UNREADY_JSON).build();
  }

}
