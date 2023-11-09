package se.yolean.kafka.keyvalue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

// Having a QuarkusTest helps us catch quarkus runtime errors when running unit tests.
// For example, configuration properties are validated by quarkus at startup
@QuarkusTest
@WithKubernetesTestServer
@QuarkusTestResource(KafkaTestResourceLifecycleManager.class)
public class QuarkusRuntimeTest {

  @Test
  void test() {
    given()
        .when().get("/q/health/ready")
        .then()
        // We'll be DOWN at startup since ConsumerAtLeastOnce has not completed the
        // startup routine immediately (stage is still "Created", not yet "Polling")
        .statusCode(503)
        .body(containsString("\"stage\": \"Created\""));
  }

}
