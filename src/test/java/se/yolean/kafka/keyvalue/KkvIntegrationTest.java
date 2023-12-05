package se.yolean.kafka.keyvalue;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.quarkus.test.kafka.KafkaCompanionResource;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;
import io.restassured.RestAssured;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import jakarta.inject.Inject;
import se.yolean.kafka.keyvalue.KafkaCache.Stage;

@Disabled // You can enable this test if you have a Docker environment!
@QuarkusTest
@QuarkusTestResource(KafkaCompanionResource.class)
@WithKubernetesTestServer
public class KkvIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(KkvIntegrationTest.class);

  @InjectKafkaCompanion
  KafkaCompanion companion;

  @Inject
  MeterRegistry registry;

  @Inject
  @Identifier("kkv")
  ConsumerAtLeastOnce consumerAtLeastOnce;

  @Test
  public void test() {
    // Initial state
    assertEquals(consumerAtLeastOnce.getStage(), Stage.Created);
    logger.debug("Producing an initial record");
    companion.produceStrings().fromRecords(new ProducerRecord<>("mytopic", "key1", "initial-value")).awaitCompletion();
    logger.debug("Done producing initial record");

    // Startup
    performAndWaitOrThrow(() -> RestAssured.get("/q/health/ready").andReturn(), res -> res.statusCode() == 200);
    assertEquals(consumerAtLeastOnce.getStage(), Stage.Polling);
    RestAssured
      .when().get("/cache/v1/raw/key1")
      .then()
      .statusCode(200)
      .body(is("initial-value"));
    assertEquals(0, registry.find("kkv.onupdate.dispatch").counter().count(), "It doesn't send updates for the inital state");

    // Updates
    companion.produceStrings().fromRecords(new ProducerRecord<>("mytopic", "key1", "another-value")).awaitCompletion();
    performAndWaitOrThrow(() -> {
      return RestAssured.get("/cache/v1/raw/key1").andReturn();
    }, res -> res.statusCode() == 200 && res.body().asString().equals("another-value"));
    assertEquals(1, registry.find("kkv.onupdate.dispatch").counter().count(), "It sends updates");

    // Update suppressions
    companion.produceStrings().fromRecords(new ProducerRecord<>("mytopic", "key1", "another-value")).awaitCompletion();
    performAndWaitOrThrow(() -> null, res -> {
      return registry.find("kkv.onupdate.suppressed").tag("suppress_reason", "value_deduplication").counter().count() == 1;
    });
    assertEquals(1, registry.find("kkv.onupdate.dispatch").counter().count(),
        "It does not send updates for duplicate values");
    assertEquals(1,
        registry.find("kkv.onupdate.suppressed").tag("suppress_reason", "value_deduplication").counter().count(),
        "It counts deduplications");

    companion.produceStrings().fromRecords(new ProducerRecord<>("mytopic", "this-has-no-key")).awaitCompletion();
    performAndWaitOrThrow(() -> null, res -> {
      return registry.find("kkv.onupdate.suppressed").tag("suppress_reason", "null_key").counter().count() == 1;
    });
    assertEquals(1, registry.find("kkv.onupdate.dispatch").counter().count(),
        "It does not send updates for null keys");
    assertEquals(1,
        registry.find("kkv.onupdate.suppressed").tag("suppress_reason", "null_key").counter().count(),
        "It counts null keys");
  }

  /**
   * This pattern is reused every time we wait for the application to react to some change
   */
  private <T> T performAndWaitOrThrow(Supplier<T> action, Predicate<T> waitUntil) {
    logger.debug("Performing some action...");
    return Uni.createFrom().item(() -> {
      T result = action.get();
      boolean ok = waitUntil.test(result);
      if (!ok) {
        throw new RuntimeException("Result did not fulfill predicate");
      }
      logger.debug("Action completed successfully");
      return result;
    }).onFailure()
      .retry()
      .withBackOff(Duration.ofSeconds(1), Duration.ofSeconds(1))
      .atMost(15)
      .await()
      .indefinitely();
  }

}
