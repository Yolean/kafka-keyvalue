// Copyright 2019 Yolean AB
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package se.yolean.kafka.keyvalue;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.smallrye.common.annotation.Identifier;

/**
 * Instead of catching and analyzing org.apache.kafka.common.errors.TimeoutException
 * we want to trigger non-liveness if we've never seen proof of a working kafka connection.
 * BUT we don't want to go non-live in case kafka fails to respond,
 * because such termination could lead to cascading failure.
 */
@Liveness
@Singleton
public class KafkaClientOnceLiveness implements HealthCheck {

  private static final Logger logger = LoggerFactory.getLogger(KafkaClientOnceLiveness.class);

  @Inject
  @Identifier("kkv")
  KafkaCache consumer;

  HealthCheckResponse ok = HealthCheckResponse.builder().name("Had a Kafka connection").up().build();
  boolean assigningSuccessWasSeen = false;

  @Override
  public HealthCheckResponse call() {
    logger.trace("Health check start", consumer);
    if (consumer != null && consumer.getStage() != null) {
      if (consumer.getStage().metricValue > ConsumerAtLeastOnce.Stage.Assigning.metricValue) {
        assigningSuccessWasSeen = true;
      }
      if (!assigningSuccessWasSeen && consumer.getStage().equals(ConsumerAtLeastOnce.Stage.Assigning)) {
        return HealthCheckResponse.builder().name("Had a Kafka connection").down().build();
      }
    }
    return ok;
  }

}
