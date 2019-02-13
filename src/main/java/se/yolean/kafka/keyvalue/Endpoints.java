package se.yolean.kafka.keyvalue;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;

import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;

import se.yolean.kafka.keyvalue.http.RestResource;

/**
 * REST resources for {@link KafkaCache}.
 */
@Path("cache/v1")
public class Endpoints implements RestResource {

  private KafkaCache cache;

  public Endpoints(KafkaCache cache) {
    this.cache = cache;
  }

  /**
   * Will eventually contain logic for reading values from other replicas in
   * partitioned caches.
   *
   * @param key To look up
   * @return the value
   * @throws NotFoundException If the key wasn't in the cache or if the value
   *                           somehow was null
   */
  private byte[] getCacheValue(String key) throws NotFoundException {
    if (key == null) {
      throw new javax.ws.rs.BadRequestException("Request key can not be null");
    }
    if (key == "") {
      throw new javax.ws.rs.BadRequestException("Request key can not be empty");
    }
    final byte[] value = cache.getValue(key);
    if (value == null) {
      throw new NotFoundException();
    }
    return value;
  }

  @GET
  @Path("/raw/{key}")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public byte[] valueByKey(@PathParam("key") final String key, @Context UriInfo uriInfo) {
    return getCacheValue(key);
  }

  /**
   * All keys in this instance (none from the partitions not represented here).
   */
  @GET()
  @Path("/keys")
  @Produces(MediaType.APPLICATION_JSON)
  public Iterator<String> keys() {
    Iterator<String> all = cache.getKeys();
    /*
     * List<String> response = new LinkedList<String>(); while (all.hasNext()) {
     * response.add(all.next().key); }
     */

    return all;
  }

  /**
   * @return Newline separated values (no keys)
   */
  @GET()
  @Path("/values")
  @Produces(MediaType.TEXT_PLAIN)
  public Response values() {
    Iterator<byte[]> values = cache.getValues();

    StreamingOutput stream = new StreamingOutput() {
      @Override
      public void write(OutputStream out) throws IOException, WebApplicationException {
        while (values.hasNext()) {
          out.write(values.next());
          out.write('\n');
        }
      }
    };
    return Response.ok(stream).build();
  }

}
