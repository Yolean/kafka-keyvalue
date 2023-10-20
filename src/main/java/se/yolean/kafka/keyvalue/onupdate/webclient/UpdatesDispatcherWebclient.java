package se.yolean.kafka.keyvalue.onupdate.webclient;

import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.mutiny.Uni;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.MultiMap;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;
import se.yolean.kafka.keyvalue.kubernetes.EndpointsWatcher;
import se.yolean.kafka.keyvalue.onupdate.UpdatesBodyPerTopic;
import se.yolean.kafka.keyvalue.onupdate.UpdatesDispatcher;

@ApplicationScoped
public class UpdatesDispatcherWebclient implements UpdatesDispatcher {

  final Logger logger = LoggerFactory.getLogger(this.getClass());

  EndpointsWatcher watcher;

  private MeterRegistry registry;

  @Inject
  UpdatesDispatcherWebclientConfig config;

  private final WebClient webClient;

  static void initMetrics(MeterRegistry registry) {
    registry.counter("kkv.target.update.failure").increment(0);
  }

  @Inject
  public UpdatesDispatcherWebclient(Vertx vertx, MeterRegistry registry, EndpointsWatcher watcher) {
    this.webClient = WebClient.create(vertx);
    this.registry = registry;
    this.watcher = watcher;

    initMetrics(registry);

    watcher.addOnReadyConsumer((update, targets) -> {
      dispatch(update, targets);
    });
  }

  @Override
  public void dispatch(UpdatesBodyPerTopic body) {
    dispatch(body, watcher.getTargets());
  }

  private void dispatch(UpdatesBodyPerTopic body, Map<String, String> targets) {
    watcher.updateUnreadyTargets(body);

    Map<String, String> headers = body.getHeaders();
    JsonObject json = new JsonObject(Buffer.buffer(body.getContent()));

    if (config.targetStaticHost().isPresent()) {
      String host = config.targetStaticHost().orElseThrow();
      int port = config.targetStaticPort();
      dispatch(json, headers, host, port).subscribe().with(item -> {
        logger.info("Successfully sent update to static host: {}", host);
      }, getDispatchFailureConsumer("static host: " + host));
    }

    targets.entrySet().stream().forEach(entry -> {
      String ip = entry.getKey();
      String name = entry.getValue();

      dispatch(json, headers, ip, config.targetServicePort()).subscribe().with(item -> {
        logger.info("Successfully sent update to {}", name);
      }, getDispatchFailureConsumer(name));
    });
  }

  private Uni<?> dispatch(JsonObject json, Map<String, String> headers, String host, int port) {
    return webClient
      .post(config.targetServicePort(), host, config.targetPath())
      .putHeaders(MultiMap.caseInsensitiveMultiMap().addAll(headers))
      .sendJsonObject(json)
      .onFailure().retry().withBackOff(Duration.ofSeconds(config.retryBackoffSeconds())).atMost(config.retryTimes());
  }

  private Consumer<Throwable> getDispatchFailureConsumer(String name) {
    return (t) -> {
      registry.counter("kkv.target.update.failure").increment();
      logger.error("Failed to send update to " + name, t);
    };
  }

  @Override
  public void close() {
    logger.info("Closing http client");
    webClient.close();
  }

}
