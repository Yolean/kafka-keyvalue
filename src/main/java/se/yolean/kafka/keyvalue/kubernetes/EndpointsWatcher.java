package se.yolean.kafka.keyvalue.kubernetes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.quarkus.runtime.StartupEvent;
import io.vertx.mutiny.core.Vertx;
import se.yolean.kafka.keyvalue.onupdate.UpdatesBodyPerTopic;

@ApplicationScoped
public class EndpointsWatcher {

  private final Logger logger = LoggerFactory.getLogger(EndpointsWatcher.class);
  private List<EndpointAddress> endpoints = List.of();
  private Map<EndpointAddress, List<UpdatesBodyPerTopic>> unreadyEndpoints = new HashMap<>();

  private List<BiConsumer<UpdatesBodyPerTopic, Map<String, String>>> onTargetReadyConsumers = new ArrayList<>();

  private final String targetServiceName;
  private final boolean watchEnabled;
  private final int watchRestartDelaySeconds;

  @Inject
  KubernetesClient client;

  @Inject
  Vertx vertx;

  private Watcher<Endpoints> watcher = null;
  private Watch watch = null;

  private boolean healthUnknown = true;
  private Counter countEvent;
  private Counter countReconnect;
  private Counter countClose;

  @Inject
  public EndpointsWatcher(EndpointsWatcherConfig config, MeterRegistry registry) {
    this.watchRestartDelaySeconds = config.watchRestartDelaySeconds();
    if (config.targetServiceName().isPresent()) {
      watchEnabled = true;
      targetServiceName = config.targetServiceName().orElseThrow();
    } else {
      watchEnabled = false;
      targetServiceName = null;
    }
    Gauge.builder("kkv.watcher.health.unknown", () -> {
      if (healthUnknown) return 1.0;
      return 0.0;
    }).register(registry);
    countEvent = Counter.builder("kkv.watcher.event").register(registry);
    countReconnect = Counter.builder("kkv.watcher.reconnect").register(registry);
    countClose = Counter.builder("kkv.watcher.close").register(registry);
    countEvent.increment(0);
    countReconnect.increment(0);
    countClose.increment(0);
  }

  void start(@Observes StartupEvent ev) {
    logger.info("EndpointsWatcher onStart");
    if (watchEnabled) {
      watch();
    } else {
      logger.info("No target service name configured, EndpointsWatcher is disabled");
    }
  }

  public void addOnReadyConsumer(BiConsumer<UpdatesBodyPerTopic, Map<String, String>> consumer) {
    onTargetReadyConsumers.add(consumer);
  }

  void handleEvent(Action action, Endpoints resource) {
    logger.debug("endpoints watch received action: {}", action.toString());

    endpoints = resource.getSubsets().stream()
        .map(subset -> subset.getAddresses())
        .flatMap(Collection::stream)
        .distinct()
        .collect(Collectors.toList());

    endpoints.forEach(address -> {
      if (unreadyEndpoints.containsKey(address)) {
        emitPendingUpdatesToNowReadyTarget(address, unreadyEndpoints.get(address));
        unreadyEndpoints.remove(address);
      }
    });

    List<EndpointAddress> receivedUnreadyEndpoints = resource.getSubsets().stream()
        .map(subset -> subset.getNotReadyAddresses())
        .flatMap(Collection::stream)
        .distinct()
        .collect(Collectors.toList());

    var pendingRemoves = new HashSet<>(unreadyEndpoints.keySet());
    receivedUnreadyEndpoints.forEach(address -> {
      logger.debug("{} is kept as unready endpoint", address);
      pendingRemoves.remove(address);

      if (!unreadyEndpoints.containsKey(address)) {
        unreadyEndpoints.put(address, new ArrayList<>());
      }
    });

    logger.debug("These endpoints are no longer unready {}", pendingRemoves);
    for (var address : pendingRemoves) {
      unreadyEndpoints.remove(address);
    }

    logger.debug("Received unready targets: {}", mapEndpointsToTargets(receivedUnreadyEndpoints));

    logger.info("Set new unready targets: {}",
        mapEndpointsToTargets(new ArrayList<EndpointAddress>(unreadyEndpoints.keySet())));
    logger.info("Set new targets: {}", mapEndpointsToTargets(endpoints));
  }

  private void watch() {
    if (watcher == null) createWatcher();
    logger.info("Starting watch");
    watch = client.endpoints().withName(targetServiceName).watch(watcher);
  }

  private void retryWatch() {
    if (watch != null) {
      var vwatch = watch;
      watch = null;
      vwatch.close();
    }

    double jitter = Math.random() * 0.2 + 1;
    long delay = Double.valueOf(jitter * watchRestartDelaySeconds * 1000).longValue();

    logger.info("Retrying watch in {} seconds ({}ms)", delay / 1000, delay);
    vertx.setTimer(delay, id -> {
      vertx.executeBlocking(() -> {
        logger.info("Retrying watch...");
        watch();
        return null;
      }).subscribe().with(item -> {
        logger.info("Successfully reconnected");
      }, e -> {
        logger.error("Failed to watch, trying again", e);
        retryWatch();
      });
    });
  }

  private void createWatcher() {
    watcher = new Watcher<Endpoints>() {
      @Override
      public void eventReceived(Action action, Endpoints resource) {
        healthUnknown = false;
        countEvent.increment();
        handleEvent(action, resource);
      }

      @Override
      public boolean reconnecting() {
        healthUnknown = true;
        countReconnect.increment();
        boolean reconnecting = Watcher.super.reconnecting();
        logger.warn("Watcher reconnecting. \"If the Watcher can reconnect itself after an error\": {}", reconnecting);
        return reconnecting;
      }

      @Override
      public void onClose(WatcherException cause) {
        healthUnknown = true;
        countClose.increment();
        logger.error("Watch closed with error", cause);
        retryWatch();
      }

      @Override
      public void onClose() {
        healthUnknown = true;
        countClose.increment();
        logger.info("Watch closed");
        retryWatch();
      }
    };
  }

  private void emitPendingUpdatesToNowReadyTarget(EndpointAddress address, List<UpdatesBodyPerTopic> updates) {
    logger.debug("Updating target {} from {} pending updates", address.getTargetRef().getName(), updates.size());

    if (updates.size() == 0) return;

    var mergedUpdate = UpdatesBodyPerTopic.merge(updates);
    logger.debug("Merged update result {}", mergedUpdate);

    onTargetReadyConsumers.forEach(consumer -> {
      consumer.accept(mergedUpdate, Map.of(address.getIp(), address.getTargetRef().getName()));
    });
  }

  public Map<String, String> getTargets() {
    return mapEndpointsToTargets(endpoints);
  }

  public Map<String, String> getUnreadyTargets() {
    return mapEndpointsToTargets(new ArrayList<>(unreadyEndpoints.keySet()));
  }

  public void updateUnreadyTargets(UpdatesBodyPerTopic body) {
    unreadyEndpoints.entrySet().forEach(entry -> {
      logger.debug("Adding pending update to unready target {}", entry.getKey().getTargetRef().getName());
      entry.getValue().add(body);
    });
  }

  /**
   * @return Endpoint IPs mapped to target names
   */
  private Map<String, String> mapEndpointsToTargets(List<EndpointAddress> endpoints) {
    return endpoints.stream()
        .collect(Collectors.toMap(
            endpoint -> endpoint.getIp(),
            endpoint -> endpoint.getTargetRef().getName(),
            (name1, name2) -> name1));
  }

}
