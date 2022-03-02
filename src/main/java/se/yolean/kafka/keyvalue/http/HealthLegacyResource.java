package se.yolean.kafka.keyvalue.http;

import java.net.URI;
import java.net.URISyntaxException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/health")
public class HealthLegacyResource {

  private final Logger logger = LoggerFactory.getLogger(HealthLegacyResource.class);

  private URI readyPath;

  public HealthLegacyResource() {
    try {
      readyPath = new URI("/q/health/ready");
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  @GET
  @Path("/ready")
  public Response redirectReady() {
    logger.warn("Legacy ready endpoint used, please switch to {}", readyPath);
    return Response.status(Status.FOUND).location(readyPath).build();
  }

}
