package se.yolean.kafka.keyvalue.kubernetes;

import java.time.Duration;
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
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
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
  private final String namespace;
  private final Duration resyncPeriod;
  private final boolean watchEnabled;

  @Inject
  KubernetesClient client;

  @Inject
  Vertx vertx;

  private SharedIndexInformer<Endpoints> informer;

  private boolean healthUnknown = true;
  private Counter countEvent;
  private Counter countReconnect;
  private Counter countClose;

  @Inject
  public EndpointsWatcher(EndpointsWatcherConfig config, MeterRegistry registry) {
    this.namespace = config.namespace();
    this.resyncPeriod = config.resyncPeriod();
    config.resyncPeriod();
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
      inform();
    } else {
      logger.info("No target service name configured, EndpointsWatcher is disabled");
    }
  }

  public boolean isWatching() {
    return informer != null && informer.isWatching();
  }

  public void addOnReadyConsumer(BiConsumer<UpdatesBodyPerTopic, Map<String, String>> consumer) {
    onTargetReadyConsumers.add(consumer);
  }

  void handleEvent(Action action, Endpoints resource) {
    logger.debug("endpoints watch received action: {}", action.toString());
    if (action.equals(Action.DELETED)) {
      clearEndpoints();
      return;
    }

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

  /**
   * Clears the collections of (unready or not) endpoints.
   *
   * REVIEW Since we're only watching a single "Endpoints" (collection of
   * addresses), a delete event means that we should clear all endpoints. If we
   * change from watching "Endpoints" to "EndpointSlices" as suggested in
   * https://kubernetes.io/docs/concepts/services-networking/service/#endpoints we
   * cannot know that the slice contains all addresses which would make this
   * method more complex.
   */
  private void clearEndpoints() {
    unreadyEndpoints.clear();
    endpoints.clear();
    logger.info("Cleared all targets");
  }

  private void inform() {
    if (informer == null) createInformer();
    logger.info("Started informer");
  }

  private void createInformer() {
    informer = client.endpoints()
      .inNamespace(namespace)
      .withName(targetServiceName)
      .inform(
        new ResourceEventHandler<Endpoints>() {

          @Override
          public void onAdd(Endpoints obj) {
            countEvent.increment();
            handleEvent(Action.ADDED, obj);
          }

          @Override
          public void onUpdate(Endpoints oldObj, Endpoints newObj) {
            countEvent.increment();
            handleEvent(Action.MODIFIED, newObj);
          }

          @Override
          public void onDelete(Endpoints obj, boolean deletedFinalStateUnknown) {
            // The "Endpoints" object we watch contains all endpoints for the target
            // service. Delete only happens if the service itself is deleted, never when
            // individual endpoint-addresses change.
            logger.warn("Endpoints onDelete {}", obj);
            handleEvent(Action.DELETED, obj);
          }

        }, resyncPeriod.toMillis()
      );
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
