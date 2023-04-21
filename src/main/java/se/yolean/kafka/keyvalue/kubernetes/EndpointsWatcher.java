package se.yolean.kafka.keyvalue.kubernetes;

import java.util.Collection;
import java.util.List;
import java.util.Map;
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

@ApplicationScoped
public class EndpointsWatcher {

  private final Logger logger = LoggerFactory.getLogger(EndpointsWatcher.class);
  private List<EndpointAddress> endpoints = List.of();

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

  private void watch() {
    client.endpoints().withName(config.targetServiceName()).watch(new Watcher<Endpoints>() {
      @Override
      public void eventReceived(Action action, Endpoints resource) {
        endpoints = resource.getSubsets().stream()
            .map(subset -> subset.getAddresses())
            .flatMap(Collection::stream)
            .distinct()
            .toList();
        logger.info("Set new targets: {}", getTargets());
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

  /**
   * @return Endpoint IPs mapped to target names
   */
  public Map<String, String> getTargets() {
    return endpoints.stream()
        .collect(Collectors.toMap(
            endpoint -> endpoint.getIp(),
            endpoint -> endpoint.getTargetRef().getName(),
            (name1, name2) -> name1));
  }

}
