package se.yolean.kafka.keyvalue.onupdate.webclient;

import java.time.Duration;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.MultiMap;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;
import se.yolean.kafka.keyvalue.kubernetes.EndpointsWatcher;
import se.yolean.kafka.keyvalue.onupdate.TargetAckFailedException;
import se.yolean.kafka.keyvalue.onupdate.UpdatesBodyPerTopic;
import se.yolean.kafka.keyvalue.onupdate.UpdatesDispatcher;

@ApplicationScoped
public class UpdatesDispatcherWebclient implements UpdatesDispatcher {

  final Logger logger = LoggerFactory.getLogger(this.getClass());

  @Inject
  EndpointsWatcher watcher;

  @Inject
  MeterRegistry registry;

  @ConfigProperty(name = "kkv.target.service.port")
  int port;

  @Inject
  UpdatesDispatcherWebclientConfig config;

  private final WebClient webClient;

  @Inject
  public UpdatesDispatcherWebclient(Vertx vertx) {
    this.webClient = WebClient.create(vertx);
  }

  @Override
  public void dispatch(String topicName, UpdatesBodyPerTopic body) throws TargetAckFailedException {
    Map<String, String> targets = watcher.getTargets();
    Map<String, String> headers = body.getHeaders();
    JsonObject json = new JsonObject(Buffer.buffer(body.getContent()));
    targets.entrySet().parallelStream().forEach(entry -> {
      String ip = entry.getKey();
      String name = entry.getValue();
      webClient
          .post(config.targetServicePort(), ip, config.targetPath())
          .putHeaders(MultiMap.caseInsensitiveMultiMap().addAll(headers))
          .sendJsonObject(json)
          .onFailure().retry().withBackOff(Duration.ofSeconds(config.retryBackoffSeconds())).atMost(config.retryTimes())
          .subscribe().with(
            item -> {
              logger.info("Successfully sent update to {}", name);
            },
            failure -> {
              registry.counter("kkv.target.update.failure").increment();
              logger.error("Failed to send update to " + name, failure);
            }
          );
    });
  }

  @Override
  public void close() {
    logger.info("Closing http client");
    webClient.close();
  }

}
