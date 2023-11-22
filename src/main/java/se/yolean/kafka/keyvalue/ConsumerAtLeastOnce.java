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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.reactive.messaging.kafka.KafkaConsumerRebalanceListener;

@ApplicationScoped
@Identifier("kkv")
public class ConsumerAtLeastOnce implements KafkaConsumerRebalanceListener, KafkaCache {

  final Logger logger = LoggerFactory.getLogger(this.getClass());

  // REVIEW This (the defaultValue) actually works without custom converters since Duration has a static parse function
  // https://github.com/eclipse/microprofile-config/blob/master/spec/src/main/asciidoc/converters.asciidoc#automatic-converters
  // The microprofile language server still gets us a red squiggly here though...
  @ConfigProperty(name = "kkv.assignments.timeout", defaultValue="90s")
  Duration assignmentsTimeout;

  @Inject
  Map<String, byte[]> cache;

  @Inject
  OnUpdate onupdate;

  @Inject
  MeterRegistry registry;

  Map<TopicPartition, Long> endOffsets = null;
  Map<TopicPartition, AtomicLong> currentOffsets = new HashMap<>(1);

  private Set<String> topics = new HashSet<>();

  private Stage stage = Stage.Created;

  private boolean pollHasUpdates = false;

  Integer getStageMetric() {
    return stage.metricValue;
  }

  void start(@Observes StartupEvent ev) {
    logger.info("Build meta, if present: branch={}, commit={}, image={}",
        System.getenv("SOURCE_BRANCH"),
        System.getenv("SOURCE_COMMIT"),
        System.getenv("IMAGE_NAME"));
    logger.info("Cache: {}", cache);

    registry.gauge("kkv.stage", this, ConsumerAtLeastOnce::getStageMetric);
    registry.gaugeCollectionSize("kkv.cache.keys", Tags.empty(), this.cache.keySet());
  }

  public void stop(@Observes ShutdownEvent ev) {
    logger.info("Stopping");
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

  /**
   * Provides offset information to kkv logic.
   *
   * @param consumer   underlying consumer
   * @param partitions set of assigned topic partitions
   */
  @Override
  public void onPartitionsAssigned(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
    if (this.endOffsets != null) {
      // REVIEW We do not handle additional partitions during application lifecycle.
      // This would only occur if the topic configuration changed.
      if (!currentOffsets.keySet().containsAll(partitions)) {
        throw new RuntimeException("Unsupported state, previously unknown partitions were assigned");
      }

      logger.warn("Partition re-assignment ignored, with no check for differences in the set of partitions");
      return;
    }
    this.stage = Stage.Assigning;
    this.endOffsets = new HashMap<>();

    Map<TopicPartition, Long> lowWaterMarksAtStart = consumer.beginningOffsets(partitions, assignmentsTimeout);
    for (TopicPartition partition : partitions) {
      topics.add(partition.topic());
      long startOffset = lowWaterMarksAtStart.get(partition);
      long position = consumer.position(partition, assignmentsTimeout);

      // Position is the offset of the next record
      this.endOffsets.put(partition, position - 1);
      this.currentOffsets.put(partition, new AtomicLong(position - 1));
      registerCurrentOffsetMetrics();

      if (position == 0) {
        logger.info("Got assigned position {} for {}; topic is empty or someone wants onupdate for existing messages", position, partition);
        this.stage = Stage.Polling;
        continue;
      }
      this.stage = Stage.Resetting;
      logger.info("Got assigned offset {} for {}; seeking to low water mark {}", position, partition, startOffset);
      consumer.seek(partition, startOffset);
    }
    onupdate.pollStart(topics);
  }

  /**
   * Registers a gauage keeping track of the current offset for each topic-partition.
   */
  void registerCurrentOffsetMetrics() {
    this.currentOffsets.forEach((topicPartition, offset) -> {
      Tags tags = Tags.of("topic", topicPartition.topic(), "partition", String.valueOf(topicPartition.partition()));
      registry.gauge("kkv.last.seen.offset", tags, offset);
    });
  }

  @Incoming("topic")
  public void consume(ConsumerRecord<String, byte[]> record) {
    // If we find a way to consume the entire batch we wouln't need the KafkaPollListener hack
    // or the pollHasUpdates instance state
    //for (ConsumerRecord<String, byte[]> record : records)  {
      try {
        boolean valueEqual = isValueEqual(record.key(), record.value());
        cacheRecord(record);

        UpdateRecord update = new UpdateRecord(record.topic(), record.partition(), record.offset(), record.key());
        long start = getEndOffset(update.getTopicPartition());
        if (record.offset() >= start) {
          handleUpdateRecord(update, valueEqual);
        } else {
          logger.trace("Suppressing onupdate for {} because start offset is {}", update, start);
        }
      } catch (RuntimeException e) {
        logger.error("Single-message processing error at {}", record);
        throw e;
      }
    // }
    if (KafkaPollListener.getIsPollEndOnce()) {
      logger.info("Poll end detected. Dispatching onUpdate.");
      if (pollHasUpdates) {
        pollHasUpdates = false;
        onupdate.sendUpdates();
        onupdate.pollStart(topics);
      } else {
        logger.info("Poll end detected. No updates to dispatch.");
      }
    }
  }

  /**
   * @return true if value is equal to the current value stored for key
   */
  boolean isValueEqual(String key, byte[] value) {
    return Arrays.equals(value, cache.get(key));
  }

  /**
   * Adds the record to cache and updates current offsets
   * @param record
   */
  void cacheRecord(ConsumerRecord<String, byte[]> record) {
    this.currentOffsets.get(new TopicPartition(record.topic(), record.partition())).set(record.offset());

    if (record.key() != null) {
      cache.put(record.key(), record.value());
    } else {
      logger.warn("Ignoring null key at topic: {}, partition: {}, offset: {}",
          record.topic(), record.partition(), record.offset());
    }
  }

  void handleUpdateRecord(UpdateRecord update, boolean valueEqual) {
    TopicPartition topicPartition = update.getTopicPartition();

    Tags tags = Tags.of("topic", update.getTopic(), "partition", "" + update.getPartition());
    Tags suppressReason = null;

    if (topicPartition == null) {
      if (logger.isTraceEnabled()) logger.trace("onNullKey {}", update.getOffset());
      suppressReason = tags.and("suppress_reason", "null_key");
    } else if (valueEqual) {
      if (logger.isTraceEnabled()) logger.trace("unchanged {} {}", update.getOffset(), update.getKey());
      suppressReason = tags.and("suppress_reason", "value_deduplication");
    }

    if (suppressReason != null) {
      registry.counter("kkv.onupdate.suppressed", suppressReason).increment();
    } else {
      if (logger.isTraceEnabled()) logger.trace("onupdate {} {}", update.getOffset(), update.getKey());
      onupdate.handle(update);
      pollHasUpdates = true;
    }
  }

  @Override
  public boolean isEndOffsetsReached() {
    return endOffsets != null && endOffsets.entrySet().stream().allMatch(entry -> {
      TopicPartition topicPartition = entry.getKey();
      Long endOffset = entry.getValue();
      Long currentOffset = getCurrentOffset(topicPartition.topic(), topicPartition.partition());
      return currentOffset != null && endOffset <= currentOffset;
    });
  }

  @Override
  public Stage getStage() {
    return this.stage;
  }

  @Override
  public Long getCurrentOffset(String topicName, int partition) {
    AtomicLong offset = currentOffsets.get(new TopicPartition(topicName, partition));
    if (offset != null) {
      return offset.get();
    } else {
      return null;
    }
  }

  @Override
  public List<TopicPartitionOffset> getCurrentOffsets() {
    var offsets = new ArrayList<TopicPartitionOffset>();
    currentOffsets.forEach((key, value) -> {
      offsets.add(new TopicPartitionOffset(key.topic(), key.partition(), value.get()));
    });

    return offsets;
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
