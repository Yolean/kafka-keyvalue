package se.yolean.kafka.keyvalue.onupdate;

import java.util.concurrent.Future;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import se.yolean.kafka.keyvalue.OnUpdate;
import se.yolean.kafka.keyvalue.UpdateRecord;

/**
 * Sends an async request and disregards the result,
 * i.e. reports success every time and is not retryable.
 */
public class OnUpdateHttpIgnoreResult implements OnUpdate {

  private String url;

  //private final Client client = ClientBuilder.newBuilder().register(JacksonFeature.class).build();
  private final Client client = ClientBuilder.newBuilder().build();

  public OnUpdateHttpIgnoreResult(String webhookUrl) {
    this.url = webhookUrl;
  }

  @Override
  public void handle(UpdateRecord update, Runnable onSuccess) {
    @SuppressWarnings("unused")
    Future<Response> res = client.target(url).request().async().post(
        Entity.entity(update, MediaType.APPLICATION_JSON_TYPE));

  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName() + '(' + this.url + ')';
  }

}
