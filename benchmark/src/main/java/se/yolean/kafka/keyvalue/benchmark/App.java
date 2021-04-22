package se.yolean.kafka.keyvalue.benchmark;

import javax.enterprise.event.Observes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

@Path("/kkv-benchmark")
public class App {

  private static final Logger logger = LoggerFactory.getLogger(App.class);

  void onStart(@Observes StartupEvent ev) {
    logger.info("The application is starting...");
  }

  void onStop(@Observes ShutdownEvent ev) {
    logger.info("The application is stopping...");
  }

  @GET
  @Produces(MediaType.TEXT_PLAIN)
  public String hello() {
    return "Hello RESTEasy";
  }

}
