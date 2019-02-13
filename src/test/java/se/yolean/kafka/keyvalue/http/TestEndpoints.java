package se.yolean.kafka.keyvalue.http;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("cache/v1")
public class TestEndpoints implements RestResource {

  @GET
  @Path("/{key}")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public byte[] value(@PathParam("key") String key) {
    if ("null".equals(key)) {
      throw new javax.ws.rs.NotFoundException("No hit for key: " + key);
    }
    return ("v-" + key).getBytes();
  }

}
