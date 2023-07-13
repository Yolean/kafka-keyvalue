package se.yolean.kafka.keyvalue.kubernetes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.StartupEvent;
import se.yolean.kafka.keyvalue.onupdate.UpdatesBodyPerTopic;

@ApplicationScoped
public class EndpointsWatcher {

  private final Logger logger = LoggerFactory.getLogger(EndpointsWatcher.class);
  private List<EndpointAddress> endpoints = List.of();
  private Map<EndpointAddress, List<UpdatesBodyPerTopic>> unreadyEndpoints = new HashMap<>();

  private List<BiConsumer<UpdatesBodyPerTopic, Map<String, String>>> onTargetReadyConsumers = new ArrayList<>();

  @ConfigProperty(name = "kkv.target.service.name")
  String serviceName;

  @Inject
  EndpointsWatcherConfig config;

  @Inject
  KubernetesClient client;

  void start(@Observes StartupEvent ev) {
    logger.info("EndpointsWatcher onStart");
    watch();
  }

  public void addOnReadyConsumer(BiConsumer<UpdatesBodyPerTopic, Map<String, String>> consumer) {
    onTargetReadyConsumers.add(consumer);
  }

  private void watch() {
    client.endpoints().withName(config.targetServiceName()).watch(new Watcher<Endpoints>() {
      @Override
      public void eventReceived(Action action, Endpoints resource) {

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

        var pendingRemoves = unreadyEndpoints.keySet();
        receivedUnreadyEndpoints.forEach(address -> {
          if (!unreadyEndpoints.containsKey(address)) {
            unreadyEndpoints.put(address, List.of());
          } else {
            pendingRemoves.add(address);
          }
        });

        for (var address : pendingRemoves) {
          unreadyEndpoints.remove(address);
        }

        logger.debug("endpoints watch received action: {}", action.toString());
        logger.debug("Received unready targets: {}", mapEndpointsToTargets(receivedUnreadyEndpoints));

        logger.info("Set new unready targets: {}", mapEndpointsToTargets(new ArrayList<EndpointAddress>(unreadyEndpoints.keySet())));
        logger.info("Set new targets: {}", mapEndpointsToTargets(endpoints));
      }

      @Override
      public void onClose(WatcherException cause) {
        // REVIEW what is a reasonable strategy here?
        logger.warn("Exiting application due to watch closed");
        logger.error(cause.getMessage());
        Quarkus.asyncExit(11);
      }
    });
  }

  private void emitPendingUpdatesToNowReadyTarget(EndpointAddress address, List<UpdatesBodyPerTopic> updates) {
    // I guess I wanna tell UpdatesDispatcherWebClient to send this now?
    logger.debug("TODO Send updates to address: {}, {}", address, updates);
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

  public void updateUnreadyTargets(UpdatesBodyPerTopic body) {
    unreadyEndpoints.values().forEach(updates -> {
      updates.add(body);
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
