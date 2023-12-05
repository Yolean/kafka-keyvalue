package se.yolean.kafka.keyvalue;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import se.yolean.kafka.keyvalue.KafkaCache.Stage;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

import io.smallrye.common.annotation.Identifier;

@Readiness
@ApplicationScoped
public class ConsumeLoopReadiness implements HealthCheck {

  @Inject
  @Identifier("kkv")
  KafkaCache consumer;

  @Override
  public HealthCheckResponse call() {

    Stage stage = consumer.getStage();

    HealthCheckResponseBuilder health = HealthCheckResponse
      .named("consume-loop")
      .withData("stage", stage.toString())
      .down();

    if (stage.metricValue >= Stage.Polling.metricValue && consumer.isEndOffsetsReached()) {
      return health.up().build();
    } else {
      return health.down().build();
    }
  }

}
