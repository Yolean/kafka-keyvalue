package se.yolean.kafka.keyvalue;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import se.yolean.kafka.tasks.Create;
import se.yolean.kafka.tasks.TopicCheck;

@Singleton
public class ConsumerAtLeastOnce implements KafkaCache, Runnable,
    // Note that this class is a dependency, not a service, so @Health must be on the CacheResource (contrary to https://quarkus.io/guides/health-guide)
    HealthCheck {

  public enum Stage {
    Created,
    CreatingConsumer,
    Initializing,
    WaitingForKafkaConnection,
    WaitingForTopics,
    WaitingForPartitionAssignments,
    Polling
  }

  final Logger logger = LoggerFactory.getLogger(this.getClass());

  @ConfigProperty(name="topics")
  List<String> topics;

  @ConfigProperty(name="metadata_timeout", defaultValue="5s")
  String   metadataTimeoutConf;
  Duration metadataTimeout;

  @ConfigProperty(name="topic_check_retries", defaultValue="12")
  int topicCheckRetries;

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

  final Thread runner;

  Stage stage = Stage.Created;

  HealthCheckResponseBuilder health = HealthCheckResponse
      .named("consume-loop")
      .up();

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
    return runner.isAlive();
  }

  void start(@Observes StartupEvent ev) {
    // workaround for Converter not working
    metadataTimeout = new se.yolean.kafka.keyvalue.config.DurationConverter().convert(metadataTimeoutConf);
    pollDuration = new se.yolean.kafka.keyvalue.config.DurationConverter().convert(pollDurationConf.get());
    minPauseBetweenPolls = new se.yolean.kafka.keyvalue.config.DurationConverter().convert(minPauseBetweenPollsConf.get());
    logger.info("Poll duration: {}", pollDuration);
    // end workaround
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
    try {
      run(consumer, cache, maxPolls);
    } catch (InterruptedException e) {
      logger.error("Consume loop got interrupted at stage {}", stage, e);
      throw new RuntimeException("Exited due to error", e);
    } catch (org.apache.kafka.common.errors.TimeoutException e) {
      logger.error("A Kafka timeout occured at stage {}", stage, e);
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
      org.apache.kafka.common.KafkaException {
    stage = Stage.Initializing;
    logger.info("At stage {} before {} polls with consumer {}", stage, polls == 0 ? "infinite" : polls, consumer);

    // This way of setting a consumer is because we've reused TopicCheck from kafka-topics-copy
    stage = Stage.WaitingForKafkaConnection; // we'd need to set this inside TopicCheck, but instead we'll probably refactor
    TopicCheck topicCheck = new TopicCheck(new Create(consumer), topics, metadataTimeout);

    int retries = 0;
    while (!topicCheck.sourceTopicsExist()) {
      logger.info("Waiting for topic existence {} ({})", topicCheck, topics);
      try {
        topicCheck.run();
      } catch (org.apache.kafka.common.errors.TimeoutException timeout) {
        if (retries == topicCheckRetries) {
          throw timeout;
        }
        logger.warn("Kafka listTopics timed out, but as topic check is our initial use of the consumer we'll retry.");
      } catch (org.apache.kafka.common.KafkaException unrecoverable) {
        logger.error("Topic check failed unrecoverably", unrecoverable);
        throw unrecoverable;
      }
      if (retries++ > topicCheckRetries) {
        throw new RuntimeException("Gave up waiting for topic existence after " + topicCheckRetries + " retries with " + metadataTimeout.getSeconds() + "s timeout");
      }
      Thread.sleep(metadataTimeout.toMillis());
    }
    logger.info("Topic {} found", topics);

    final Map<TopicPartition, Long> nextUncommitted = new HashMap<>(1);

    consumer.subscribe(topics, new ConsumerRebalanceListener() {

      @Override
      public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
        logger.info("Got revoked {}", partitions);
      }

      @Override
      public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
        logger.info("Got partition assignments");
        partitions.forEach(p -> {
          long next = consumer.position(p, metadataTimeout);
          nextUncommitted.put(p, next);
          logger.info("Got an initial offset {}-{}-{} to start onupdate from", p.topic(), p.partition(), next);
        });
        consumer.seekToBeginning(partitions);
      }

    });

    consumer.poll(Duration.ofMillis(1)); // Do we need one poll for subscribe to happen?
    long pollEndTime = System.currentTimeMillis();

    for (long n = 0; polls == 0 || n < polls; n++) {

      // According to "Detecting Consumer Failures" in https://kafka.apache.org/22/javadoc/index.html?org/apache/kafka/clients/consumer/KafkaConsumer.html
      // there seems to be need for a pause between polls (?)
      long wait = pollEndTime - System.currentTimeMillis() + minPauseBetweenPolls.toMillis();
      if (wait > 0) Thread.sleep(wait);

      onupdate.pollStart(topics);

      ConsumerRecords<String, byte[]> polled = consumer.poll(pollDuration);
      pollEndTime = System.currentTimeMillis();
      int count = polled.count();
      logger.debug("Polled {} records", count);

      if (nextUncommitted.isEmpty()) {
        if (count > 0) throw new IllegalStateException("Received " + count + " records prior to an assigned partitions event");
        logger.info("Haven't got partition assignments yet (metadata timeout {}s)", metadataTimeout.getSeconds());
        Thread.sleep(metadataTimeout.toMillis());
        continue;
      }

      Iterator<ConsumerRecord<String, byte[]>> records = polled.iterator();
      while (records.hasNext()) {
        ConsumerRecord<String, byte[]> record = records.next();
        UpdateRecord update = new UpdateRecord(record.topic(), record.partition(), record.offset(), record.key());
        cache.put(record.key(), record.value());
		    Long start = nextUncommitted.get(update.getTopicPartition());
        if (start == null) {
          throw new IllegalStateException("There's no start offset for " + update.getTopicPartition() + ", at consumed offset " + update.getOffset() + " key " + update.getKey());
        }
        if (record.offset() >= start) {
          onupdate.handle(update);
        } else {
          logger.info("Suppressing onupdate for {} because start offset is {}", update, start);
        }
      }

      try {
        onupdate.pollEndBlockingUntilTargetsAck();
      } catch (RuntimeException e) {
        logger.error("Failed onupdate ack. App should exit.", e);
        throw e;
      }

      consumer.commitSync();

      // Next poll ...
    }

  }

  @Override
  public Long getCurrentOffset(String topicName, int partition) {
    throw new UnsupportedOperationException("TODO implement");
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
