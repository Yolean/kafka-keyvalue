package se.yolean.kafka.keyvalue;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.prometheus.client.Counter;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import se.yolean.kafka.keyvalue.metrics.KafkaMetrics;

//@org.eclipse.microprofile.health.Health // See HealthProxy
@Singleton
public class ConsumerAtLeastOnce implements KafkaCache, Runnable,
    HealthCheck {

  public enum Stage {
    Created,
    CreatingConsumer,
    Initializing,
    WaitingForKafkaConnection,
    Assigning,
    Resetting,
    StartingPoll,
    PollingHistorical,
    Polling,
  }

  final Logger logger = LoggerFactory.getLogger(this.getClass());

  @ConfigProperty(name="metrics_enabled", defaultValue="true")
  boolean metricsEnabled;

  @ConfigProperty(name="topic") Optional<String> topic;
  @ConfigProperty(name="topic1") Optional<String> topic1;
  @ConfigProperty(name="topic2") Optional<String> topic2;
  @ConfigProperty(name="topic3") Optional<String> topic3;
  @ConfigProperty(name="topic4") Optional<String> topic4;
  @ConfigProperty(name="topic5") Optional<String> topic5;
  @ConfigProperty(name="topic6") Optional<String> topic6;
  @ConfigProperty(name="topic7") Optional<String> topic7;
  @ConfigProperty(name="topic8") Optional<String> topic8;
  @ConfigProperty(name="topic9") Optional<String> topic9;

  @ConfigProperty(name="metadata_timeout", defaultValue="5s")
  String   metadataTimeoutConf;
  Duration metadataTimeout;

  @ConfigProperty(name="poll_duration", defaultValue="5s")
  javax.inject.Provider<String>   pollDurationConf;
  Duration pollDuration;

  @ConfigProperty(name="min_pause_between_polls", defaultValue="1s")
  javax.inject.Provider<String>   minPauseBetweenPollsConf;
  Duration minPauseBetweenPolls;

  @ConfigProperty(name="max_polls", defaultValue="0")
  long maxPolls = 0;

  @Inject
  //@javax.inject.Named("consumer")
  Properties consumerProps;

  @Inject
  //@javax.inject.Named("cache")
  Map<String, byte[]> cache;

  @Inject
  OnUpdate onupdate;

  //static final Counter pollCount = Counter.build()
  //    .name("kkv_poll_count").help("Total number of polls executed").register();

  List<String> topics;

  KafkaMetrics metrics;

  final Thread runner;

  Stage stage = Stage.Created;

  HealthCheckResponseBuilder health = HealthCheckResponse
      .named("consume-loop")
      .up();

  Map<TopicPartition,Long> currentOffsets = new HashMap<>(1);

  public ConsumerAtLeastOnce() {
    runner = new Thread(this, "kafkaclient");
  }

  /**
   * https://github.com/eclipse/microprofile-health to trigger termination
   */
  @Override
  public HealthCheckResponse call() {
    if (!runner.isAlive()) {
      health = health.down();
    }
    return health.withData("stage", stage.toString()).build();
  }

  /**
   * TODO the essential criteria here is that we've consumed everything up to our start offset
   * so that the cache is consistent.
   *
   * @return true if cache appears up-to-date
   */
  public boolean isReady() {
    return runner.isAlive() && stage == Stage.Polling;
  }

  void topicsFromConfig() {
    topics = Arrays.asList(
        topic.orElse(null),
        topic1.orElse(null),
        topic2.orElse(null),
        topic3.orElse(null),
        topic4.orElse(null),
        topic5.orElse(null),
        topic6.orElse(null),
        topic7.orElse(null),
        topic8.orElse(null),
        topic9.orElse(null))
        .stream()
        .filter(t -> t != null)
        .collect(Collectors.toList());
    if (topics.size() == 0) {
      throw new IllegalStateException("At least one topic or topicX config must be set");
    }
  }

  void start(@Observes StartupEvent ev) {
    // workaround for Converter not working
    metadataTimeout = new se.yolean.kafka.keyvalue.config.DurationConverter().convert(metadataTimeoutConf);
    pollDuration = new se.yolean.kafka.keyvalue.config.DurationConverter().convert(pollDurationConf.get());
    minPauseBetweenPolls = new se.yolean.kafka.keyvalue.config.DurationConverter().convert(minPauseBetweenPollsConf.get());
    logger.info("Poll duration: {}", pollDuration);
    // end workaround
    topicsFromConfig();
    logger.info("Started. Topics: {}", topics);
    logger.info("Cache: {}", cache);
    runner.start();
  }

  public void stop(@Observes ShutdownEvent ev) {
    logger.info("Stopping");
  }

  /**
   * (Re)set all state and consume to cache, cheaper than restarting the whole application,
   * and good for integration testing.
   *
   * Should log exceptions with a meaningful message, and re-throw for {@link Thread#setUncaughtExceptionHandler(java.lang.Thread.UncaughtExceptionHandler)}.
   *
   * No thread management should happen within this loop (except maybe in outbound HTTP requests).
   */
  @Override
  public void run() {
    stage = Stage.CreatingConsumer;
    KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(consumerProps);
    if (metricsEnabled) {
      metrics = new KafkaMetrics(consumer);
    }
    try {
      run(consumer, cache, maxPolls);
    } catch (InterruptedException e) {
      logger.error("Consume loop got interrupted at stage {}", stage, e);
      throw new RuntimeException("Exited due to error", e);
    } catch (org.apache.kafka.common.errors.TimeoutException e) {
      logger.error("A Kafka timeout occured at stage {}", stage, e);
      throw e;
    } catch (org.apache.kafka.clients.consumer.NoOffsetForPartitionException e) {
      logger.error("Offset strategy is '{}' and a partition had no offset for group id: {}",
          consumerProps.get(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG),
          consumerProps.get(org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG),
          e);
      throw e;
    } catch (org.apache.kafka.common.KafkaException e) {
      logger.error("Unrecoverable kafka error at stage {}", stage, e);
      throw e;
    } catch (RuntimeException e) {
      logger.error("Unrecognized error at stage {}", stage, e);
      throw e;
    } finally {
      logger.info("Closing consumer ...");
      consumer.close();
      logger.info("Consumer closed at stage {}; Use liveness probes with /health for app termination", stage);
    }
  }

  void run(final KafkaConsumer<String, byte[]> consumer, final Map<String, byte[]> cache, final long polls) throws
      InterruptedException,
      org.apache.kafka.common.errors.TimeoutException,
      org.apache.kafka.clients.consumer.NoOffsetForPartitionException,
      org.apache.kafka.common.KafkaException {

    stage = Stage.Initializing;
    logger.info("At stage {} before {} polls with consumer {}", stage, polls == 0 ? "infinite" : polls, consumer);

    stage = Stage.WaitingForKafkaConnection;
    Map<String, List<PartitionInfo>> allTopics = consumer.listTopics(metadataTimeout);
    if (allTopics == null) throw new IllegalStateException("Got null topics list from consumer. Expected a throw.");

    // We might want this to cause retries instead of crashloop, if a full restart is too frequent, expensive or slow
    if (allTopics.size() == 0) throw new NoMatchingTopicsException(topics, allTopics);

    stage = Stage.Assigning;
    List<TopicPartition> assign = new LinkedList<>();
    for (String t : topics) {
      if (!allTopics.containsKey(t)) throw new NoMatchingTopicsException(topics, allTopics);
      for (PartitionInfo p : allTopics.get(t)) {
        assign.add(new TopicPartition(t, p.partition()));
      }
    }
    logger.info("Topics {} found with partitions {}", topics, assign);
    consumer.assign(assign);

    final Map<TopicPartition, Long> nextUncommitted = new HashMap<>(1);
    final Set<TopicPartition> lastCommittedNotReached = new HashSet<>(1);

    stage = Stage.Resetting;
    for (TopicPartition tp : assign) {
      long next = consumer.position(tp, metadataTimeout);
      logger.info("Next offset for {} is {}", tp, next);
      nextUncommitted.put(tp, next);
      lastCommittedNotReached.add(tp);
    }
    consumer.seekToBeginning(assign);

    stage = Stage.StartingPoll;
    long pollEndTime = System.currentTimeMillis();

    for (long n = 0; polls == 0 || n < polls; n++) {

      // According to "Detecting Consumer Failures" in https://kafka.apache.org/22/javadoc/index.html?org/apache/kafka/clients/consumer/KafkaConsumer.html
      // there seems to be need for a pause between polls (?)
      // - But there is no such pause in any examples
      // - Anyway let's keep it because we'll do onupdate HTTP requests
      long wait = pollEndTime - System.currentTimeMillis() + minPauseBetweenPolls.toMillis();
      if (wait > 0) Thread.sleep(wait);

      stage = lastCommittedNotReached.isEmpty() ? Stage.Polling : Stage.PollingHistorical;

      onupdate.pollStart(topics);

      ConsumerRecords<String, byte[]> polled = consumer.poll(pollDuration);
      pollEndTime = System.currentTimeMillis();
      logger.debug("Polled {} records", polled.count());

      Iterator<ConsumerRecord<String, byte[]>> records = polled.iterator();
      while (records.hasNext()) {
        ConsumerRecord<String, byte[]> record = records.next();
        UpdateRecord update = new UpdateRecord(record.topic(), record.partition(), record.offset(), record.key());
        toStats(update);
        cache.put(record.key(), record.value());
		    Long start = nextUncommitted.get(update.getTopicPartition());
        if (start == null) {
          throw new IllegalStateException("There's no start offset for " + update.getTopicPartition() + ", at consumed offset " + update.getOffset() + " key " + update.getKey());
        }
        if (record.offset() >= start) {
          onupdate.handle(update);
        } else {
          if (record.offset() == start - 1) {
            logger.info("Reached last historical message for {} at offset {}", update.getTopicPartition(), update.getOffset());
            lastCommittedNotReached.remove(update.getTopicPartition());
          }
          logger.trace("Suppressing onupdate for {} because start offset is {}", update, start);
        }
      }

      try {
        onupdate.pollEndBlockingUntilTargetsAck();
      } catch (RuntimeException e) {
        logger.error("Failed onupdate ack. App should exit.", e);
        throw e;
      }

      consumer.commitSync();

      //pollCount.inc();
      // Next poll ...
    }

  }

  private void toStats(UpdateRecord update) {
    currentOffsets.put(update.getTopicPartition(), update.getOffset());
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

  public KafkaMetrics getMetrics() {
    return metrics;
  }

}
