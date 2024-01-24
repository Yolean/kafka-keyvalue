package se.yolean.kafka.keyvalue.kubernetes;

import static org.junit.Assert.assertEquals;

import java.time.Duration;
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

  @Test
  void test() {
    assertEquals(0, (int) registry.counter("kkv.watcher.close").count());
    performAndWaitOrThrow("Waiting for readiness", () -> {
      return RestAssured.get("/q/health/ready").andReturn();
    }, res -> res.statusCode() == 200);

    performAndWaitOrThrow("Waiting for EndpointsWatcher to pick up endpoints", () -> {
      return registry.find("kkv.watcher.health.unknown").gauge().value();
    }, res -> res.equals(0d));

    logger.info("Restarting k3s");
    k3s.getDockerClient().restartContainerCmd(k3s.getContainerId()).exec();

    performAndWaitOrThrow("Waiting for EndpointsWatcher to lose its watch", () -> {
      return registry.find("kkv.watcher.health.unknown").gauge().value();
    }, res -> res.equals(1d));

    performAndWaitOrThrow("Waiting for the api server to respond after the restart", () -> {
      return client.namespaces().list().toString();
    }, res -> res != null);

    // A simple way to cause events is to rollout restart some deployment
    client.apps().deployments().inNamespace("kube-system").withName("metrics-server").rolling().restart();
    performAndWaitOrThrow("Waiting for EndpointsWatcher to recover and pick up endpoints changes", () -> {
      return registry.find("kkv.watcher.health.unknown").gauge().value();
    }, res -> res.equals(0d));
  }

  /**
   * This pattern is reused every time we wait for the application to react to some change
   */
  private <T> T performAndWaitOrThrow(String name, Supplier<T> action, Predicate<T> waitUntil) {
    logger.debug(name);
    return Uni.createFrom().item(() -> {
      T result = action.get();
      boolean ok = waitUntil.test(result);
      if (!ok) {
        throw new RuntimeException(String.format("Result did not fulfill predicate for action \"%s\"", name));
      }
      logger.debug("Action \"{}\" completed successfully", name);
      return result;
    }).onFailure()
      .retry()
      .withBackOff(Duration.ofSeconds(1), Duration.ofSeconds(1))
      .atMost(30)
      .await()
      .indefinitely();
  }

  public static class EndpointsWatcherEnabledTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of("kkv.target.service.name", "metrics-server");
    }

  }

}
