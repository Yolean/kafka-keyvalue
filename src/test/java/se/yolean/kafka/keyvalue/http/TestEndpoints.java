package se.yolean.kafka.keyvalue.http;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("cache")
public class TestEndpoints implements RestResource {

  @GET
  @Path("/{key}")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public byte[] value(String key) {
    return ("v-" + key).getBytes();
  }

}
