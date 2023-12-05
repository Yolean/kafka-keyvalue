package se.yolean.kafka.keyvalue.http;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.runtime.Quarkus;

@Path("/_admin/v1")
public class AdminResource {

  private final Logger logger = LoggerFactory.getLogger(AdminResource.class);

  @POST
  @Path("/shutdown")
  public void shutdown() {
    logger.info("Shutdown requested");
    Quarkus.asyncExit(0);
  }

  @POST
  @Path("/shutdown/{exitcode}")
  public void shutdown1(@PathParam("exitcode") byte exitcode) {
    logger.info("Shutdown reqested with exit code {}", exitcode);
    Quarkus.asyncExit(exitcode);
  }

}
