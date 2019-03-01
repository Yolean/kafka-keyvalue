package se.yolean.kafka.keyvalue.onupdate;

import java.util.concurrent.Future;

import javax.ws.rs.client.AsyncInvoker;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;

import se.yolean.kafka.keyvalue.UpdateRecord;

/**
 * To configure timeouts we need to know which JAX-RS impl we're using:
 *
 * https://stackoverflow.com/questions/22672664/setting-request-timeout-for-jax-rs-2-0-client-api
 */
public class HttpTargetRequestInvokerJersey implements HttpTargetRequestInvoker {

  private final String string;
  private final AsyncInvoker async;

  public HttpTargetRequestInvokerJersey(
      String onupdateTargetUrl,
      int connectTimeoutMilliseconds,
      int readTimeoutMilliseconds) {
    ClientConfig configuration = new ClientConfig();
    configuration.property(ClientProperties.CONNECT_TIMEOUT, connectTimeoutMilliseconds);
    configuration.property(ClientProperties.READ_TIMEOUT, readTimeoutMilliseconds);
    Client client = ClientBuilder.newClient(configuration);

    WebTarget target = client.target(onupdateTargetUrl);
    this.async = target.request().async();
    this.string = "" + connectTimeoutMilliseconds + ',' + readTimeoutMilliseconds + ',' + onupdateTargetUrl;
  }

  @Override
  public String toString() {
    return string;
  }

  @Override
  public Future<Response> postUpdate(UpdateRecord update) {
    return async.post(Entity.entity(update, MediaType.APPLICATION_JSON_TYPE));
  }

}
