package se.yolean.kafka.keyvalue.http;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

@Path("/healthz")
public class HealtzResource {

  public static final String HEALTHZ_CONTENT_TYPE = "application/json";
  public static final byte[] OK_JSON = "{\"ok\":true}".getBytes();

  @GET
  public Response ok() {
    return Response.ok(OK_JSON, HEALTHZ_CONTENT_TYPE).build();
  }

}
