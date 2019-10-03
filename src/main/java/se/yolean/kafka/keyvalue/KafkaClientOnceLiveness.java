package se.yolean.kafka.keyvalue;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

/**
 * Instead of catching and analyzing org.apache.kafka.common.errors.TimeoutException
 * we want to trigger non-liveness if we've never seen proof of a working kafka connection.
 * BUT we don't want to go non-live in case kafka fails to respond,
 * because such termination could lead to cascading failure.
 */
@Liveness
@Singleton
public class KafkaClientOnceLiveness implements HealthCheck {

  @Inject
  ConsumerAtLeastOnce consumer;

  HealthCheckResponse ok = HealthCheckResponse.builder().name("Had a Kafka connection").up().build();
  boolean assigningSuccessWasSeen = false;

  @Override
  public HealthCheckResponse call() {
    if (consumer != null && consumer.stage != null) {
      if (consumer.stage.metricValue > ConsumerAtLeastOnce.Stage.Assigning.metricValue) {
        assigningSuccessWasSeen = true;
      }
      if (!assigningSuccessWasSeen && consumer.stage.equals(ConsumerAtLeastOnce.Stage.Assigning)) {
        return HealthCheckResponse.builder().name("Had a Kafka connection").down().build();
      }
    }
    return ok;
  }

}
