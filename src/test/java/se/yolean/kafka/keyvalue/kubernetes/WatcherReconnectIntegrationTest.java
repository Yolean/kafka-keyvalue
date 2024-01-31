package se.yolean.kafka.keyvalue.kubernetes;

import static org.junit.Assert.assertEquals;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.k3s.K3sContainer;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import se.yolean.kafka.keyvalue.testresources.InjectK3sContainer;
import se.yolean.kafka.keyvalue.testresources.K3sTestResource;

@Tag("devservices")
@QuarkusTest
@QuarkusTestResource(value = K3sTestResource.class, restrictToAnnotatedClass = true)
@TestProfile(WatcherReconnectIntegrationTest.EndpointsWatcherEnabledTestProfile.class)
public class WatcherReconnectIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(WatcherReconnectIntegrationTest.class);

  @Inject
  MeterRegistry registry;

  @InjectK3sContainer
  K3sContainer k3s;

  @Inject
  KubernetesClient client;

  @Inject
  EndpointsWatcherConfig config;

  @Inject
  EndpointsWatcher watcher;

  @Test
  void test() {
    assertEquals(0, (int) registry.counter("kkv.watcher.close").count());
    performAndWaitOrThrow("Waiting for readiness", () -> {
      return RestAssured.get("/q/health/ready").andReturn();
    }, res -> res.statusCode() == 200, 10);

    logger.info("initial targets: {}", watcher.getTargets());
    performAndWaitOrThrow("Waiting for the pod to be ready so that EndpointsWatcher picks up  the target", () -> {
      return watcher.getTargets();
    }, (targets) -> targets.size() == 1, 25);

    logger.info("Restarting k3s to validate reconnect behavior");
    k3s.getDockerClient().restartContainerCmd(k3s.getContainerId()).exec();
    performAndWaitOrThrow("Waiting for the api server to respond after the restart", () -> {
      return client.namespaces().list().toString();
    }, res -> res != null, 5);
    performAndWaitOrThrow("Waiting for the informer to recover", () -> watcher.isWatching(), watching -> watching, 5);
    performAndWaitOrThrow("Waiting for EndpointsWatcher to find target", () -> watcher.getTargets(), (targets) -> {
      return targets.size() == 1;
    }, 5);

    // A simple way to cause events is to rollout restart some deployment
    logger.info("Restarting deployment to provoke endpoint changes");
    client.apps().deployments().inNamespace("kube-system").withName("metrics-server").rolling().restart();
    performAndWaitOrThrow("Waiting for the informer to lose old targets", () -> watcher.getTargets(), (targets) -> {
      return targets.size() == 0;
    }, 15);
    performAndWaitOrThrow("Waiting for the new target to appear", () -> watcher.getTargets(), (list) -> {
      return list.size() == 1;
    }, 60);

    client.services().inNamespace("kube-system").withName("metrics-server").delete();
    performAndWaitOrThrow("Waiting for endpoints to be deleted", () -> watcher.getTargets(), (list) -> {
      return list.size() == 0;
    }, 5);
  }

  /**
   * This pattern is reused every time we wait for the application to react to some change
   */
  private <T> T performAndWaitOrThrow(String name, Supplier<T> action, Predicate<T> waitUntil, int attempts) {
    logger.debug(name);
    List<Boolean> att = new ArrayList<>();
    return Uni.createFrom().item(() -> {
      att.add(true);
      T result = action.get();
      boolean ok = waitUntil.test(result);
      if (!ok) {
        throw new RuntimeException(String.format("Result did not fulfill predicate for action \"%s\"", name));
      }
      logger.debug("Action \"{}\" completed successfully after {} attempts", name, att.size());
      return result;
    }).onFailure()
      .retry()
      .withBackOff(Duration.ofSeconds(1), Duration.ofSeconds(2))
      .atMost(attempts)
      .await()
      .indefinitely();
  }

  public static class EndpointsWatcherEnabledTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      // Here we find existing pods in the k3s cluster
      return Map.of(
        "kkv.target.service.name", "metrics-server",
        "kkv.namespace", "kube-system"
      );
    }

  }

}
