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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.common.annotation.Identifier;

@ApplicationScoped
public class ConsumerAtLeastOnce implements KafkaCache, HealthCheck {

  public enum Stage {
    Created (10),
    CreatingConsumer (20),
    Initializing (30),
    WaitingForKafkaConnection (40),
    Assigning (50),
    Resetting (60),
    InitialPoll (70),
    PollingHistorical (80),
    Polling (90);

    final int metricValue;
    Stage(int metricValue) {
      this.metricValue = metricValue;
    }
  }

  final Logger logger = LoggerFactory.getLogger(this.getClass());

  @Inject
  Map<String, byte[]> cache;

  @Inject
  OnUpdate onupdate;

  @Inject
  @Identifier("kkv.rebalancer")
  RebalanceListener rebalanceListener;

  List<String> topics;

  Stage stage = Stage.Created;

  HealthCheckResponseBuilder health = HealthCheckResponse
      .named("consume-loop")
      .down();

  Map<TopicPartition,Long> currentOffsets = new HashMap<>(1);

  private final Counter meterNullKeys;

  public ConsumerAtLeastOnce(MeterRegistry registry) {
    registry.gauge("kkv.stage", this, ConsumerAtLeastOnce::getStageMetric);
    this.meterNullKeys = registry.counter("kkv.null.keys");
  }

  Integer getStageMetric() {
    return stage.metricValue;
  }

  void start(@Observes StartupEvent ev) {
    logger.info("Build meta, if present: branch={}, commit={}, image={}",
        System.getenv("SOURCE_BRANCH"),
        System.getenv("SOURCE_COMMIT"),
        System.getenv("IMAGE_NAME"));
    logger.info("Cache: {}", cache);
  }

  public void stop(@Observes ShutdownEvent ev) {
    logger.info("Stopping");
  }

  public boolean isReady() {
    return stage == Stage.Polling;
  }

  /**
   * https://github.com/eclipse/microprofile-health to trigger termination
   */
  @Override
  public HealthCheckResponse call() {
    if (this.isReady()) {
      health = health.up();
    } else {
      health = health.down();
    }
    return health.withData("stage", stage.toString()).build();
  }

  @Incoming("topic")
  public void consume(ConsumerRecord<String, byte[]> record) {

        UpdateRecord update = new UpdateRecord(record.topic(), record.partition(), record.offset(), record.key());
        toStats(update);
        if (update.getKey() != null) {
          cache.put(record.key(), record.value());
        }
        long start = rebalanceListener.getEndOffset(update.getTopicPartition());
        if (record.offset() >= start) {
          if (update.getKey() != null) {
            onupdate.handle(update);
          } else {
            onNullKey(update);
          }
        } else {
          if (record.offset() == start - 1) {
            logger.info("Reached last historical message for {} at offset {}", update.getTopicPartition(), update.getOffset());
            // TODO do we want to restore this tracking from the old consumer logic?
            // lastCommittedNotReached.remove(update.getTopicPartition());
          }
          logger.trace("Suppressing onupdate for {} because start offset is {}", update, start);
        }


  }

  private void toStats(UpdateRecord update) {
    currentOffsets.put(update.getTopicPartition(), update.getOffset());
  }

  void onNullKey(UpdateRecord update) {
    meterNullKeys.increment();
    logger.error("Ignoring null key at {}", update);
  }

  @Override
  public Long getCurrentOffset(String topicName, int partition) {
    return currentOffsets.get(new TopicPartition(topicName, partition));
  }

  @Override
  public byte[] getValue(String key) {
    return cache.get(key);
  }

  @Override
  public Iterator<String> getKeys() {
    return cache.keySet().iterator();
  }

  @Override
  public Iterator<byte[]> getValues() {
    return cache.values().iterator();
  }

}
