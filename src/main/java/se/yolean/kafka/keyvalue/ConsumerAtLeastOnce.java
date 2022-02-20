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

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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
import io.smallrye.reactive.messaging.kafka.KafkaConsumerRebalanceListener;

@ApplicationScoped
@Identifier("kkv")
public class ConsumerAtLeastOnce implements KafkaConsumerRebalanceListener, KafkaCache, HealthCheck {

  public enum Stage {
    Created (10),
    //CreatingConsumer (20),
    //Initializing (30),
    //WaitingForKafkaConnection (40),
    Assigning (50),
    Resetting (60),
    //InitialPoll (70),
    PollingHistorical (80),
    Polling (90);

    final int metricValue;
    Stage(int metricValue) {
      this.metricValue = metricValue;
    }
  }

  final Logger logger = LoggerFactory.getLogger(this.getClass());

  @ConfigProperty(name = "kkc.assignments.timeout", defaultValue="90s")
  private Duration assignmentsTimeout;

  @Inject
  Map<String, byte[]> cache;

  @Inject
  OnUpdate onupdate;

  private Map<TopicPartition, Long> endOffsets = null;

  private Map<TopicPartition, Long> lowWaterMarkAtStart = null;

  private boolean readinessOkOnResetting = false;

  private Set<String> topics = new HashSet<>();

  Stage stage = Stage.Created;

  HealthCheckResponseBuilder health = HealthCheckResponse
      .named("consume-loop")
      .down();

  Map<TopicPartition,Long> currentOffsets = new HashMap<>(1);

  private boolean pollHasUpdates = false;

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
    if (readinessOkOnResetting && this.stage == Stage.Resetting) {
      return true;
    }
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

  /**
   * @return The last offset that targets are _not_ interested in onupdate for
   */
  public long getEndOffset(TopicPartition topicPartition) {
    if (this.endOffsets == null) {
      throw new IllegalStateException("Waiting for partition assignment");
    }
    if (!endOffsets.containsKey(topicPartition)) {
      throw new IllegalStateException("Topic-partition " + topicPartition + " not found in " + endOffsets.keySet());
    }
    return endOffsets.get(topicPartition);
  }

  public long getLowWaterMarkAtStart(TopicPartition topicPartition) {
    if (this.lowWaterMarkAtStart == null) {
      throw new IllegalStateException("Waiting for partition assignment");
    }
    if (!lowWaterMarkAtStart.containsKey(topicPartition)) {
      throw new IllegalStateException("Topic-partition " + topicPartition + " not found in low watermarks " + lowWaterMarkAtStart.keySet());
    }
    return lowWaterMarkAtStart.get(topicPartition);
  }

  /**
   * Provides offset information to kkv logic.
   *
   * @param consumer   underlying consumer
   * @param partitions set of assigned topic partitions
   */
  @Override
  public void onPartitionsAssigned(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
    if (this.endOffsets != null) {
      logger.warn("Partition re-assignment ignored, with no check for differences in the set of partitions");
      return;
    }
    this.stage = Stage.Assigning;
    this.endOffsets = new HashMap<>();
    this.lowWaterMarkAtStart = consumer.beginningOffsets(partitions, assignmentsTimeout);
    for (TopicPartition partition : partitions) {
      topics.add(partition.topic());
      long startOffset = getLowWaterMarkAtStart(partition);
      long position = consumer.position(partition, assignmentsTimeout);
      this.endOffsets.put(partition, position);
      if (position == 0) {
        logger.info("Got assigned offset {} for {}; topic is empty or someone wants onupdate for existing messages", position, partition);
        this.stage = Stage.Polling;
        continue;
      }
      this.stage = Stage.Resetting;
      logger.info("Got assigned offset {} for {}; seeking to low water mark {}", position, partition, startOffset);
      if (startOffset > 0) this.readinessOkOnResetting = true;
      consumer.seek(partition, startOffset);
    }
    onupdate.pollStart(topics);
  }

  @Incoming("topic")
  public void consume(ConsumerRecord<String, byte[]> record) {
    // If we find a way to consume the entire batch we wouln't need the KafkaPollListener hack
    // or the pollHasUpdates instance state
    //for (ConsumerRecord<String, byte[]> record : records)  {
      try {
        UpdateRecord update = new UpdateRecord(record.topic(), record.partition(), record.offset(), record.key());
        toStats(update);
        if (update.getKey() != null) {
          cache.put(record.key(), record.value());
        }
        long start = getEndOffset(update.getTopicPartition());
        if (record.offset() >= start) {
          if (update.getKey() != null) {
            if (logger.isTraceEnabled()) logger.trace("onupdate {}", record.offset());
            onupdate.handle(update);
            pollHasUpdates = true;
          } else {
            if (logger.isTraceEnabled()) logger.debug("onNullKey {}", record.offset());
            onNullKey(update);
          }
        } else {
          if (record.offset() == start - 1) {
            this.stage = Stage.Polling;
            logger.info("Reached last historical message for {} at offset {}", update.getTopicPartition(), update.getOffset());
            this.readinessOkOnResetting = false;
            // TODO do we want to restore this tracking from the old consumer logic?
            // lastCommittedNotReached.remove(update.getTopicPartition());
          } else {
            this.stage = Stage.PollingHistorical;
          }
          logger.trace("Suppressing onupdate for {} because start offset is {}", update, start);
        }
      } catch (RuntimeException e) {
        logger.error("Single-message processing error at {}", record);
        throw e;
      }
    // }
    if (KafkaPollListener.getIsPollEndOnce()) {;
      if (pollHasUpdates) {
        pollHasUpdates = false;
        logger.info("Poll end detected. Dispatching onUpdate.");
        onupdate.pollEndBlockingUntilTargetsAck();
        onupdate.pollStart(topics);
      } else {
        logger.info("Poll end detected. No updates to dispatch.");
      }
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
