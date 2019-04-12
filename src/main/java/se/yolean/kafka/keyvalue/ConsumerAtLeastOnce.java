package se.yolean.kafka.keyvalue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.yolean.kafka.tasks.Create;
import se.yolean.kafka.tasks.TopicCheck;

/**
 *
 */
public class ConsumerAtLeastOnce implements Runnable {

  static final Logger logger = LoggerFactory.getLogger(ConsumerAtLeastOnce.class);

  Properties consumerProps;

  Collection<String> topics;

  Duration metadataTimeout;

  Duration pollDuration;

  long maxPolls = 0;

  OnUpdate onupdate;

  Map<String, byte[]> cache;

  /**
   * (Re)set all state and consume to cache, cheaper than restarting the whole application.
   */
  @Override
  public void run() {
	KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(consumerProps);
    try {
      runThatThrows(consumer, maxPolls);
    } catch (InterruptedException e) {
      throw new IllegalStateException("No error handling for this error", e);
    } finally {
      logger.info("Closing consumer");
      consumer.close();
    }
  }

  void runThatThrows(final KafkaConsumer<String, byte[]> consumer, final long polls) throws InterruptedException {
    logger.info("Running");

    TopicCheck topicCheck = new TopicCheck(new Create() {
      @Override
      public KafkaConsumer<? extends Object, ? extends Object> getConsumer() {
        return consumer;
      }
    }, new ArrayList<>(topics), metadataTimeout);

    while (!topicCheck.sourceTopicsExist()) {
      topicCheck.run();
      logger.info("Waiting for topic existence {} ({})", topicCheck, topics);
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
    	logger.info("Got partition assignment");
        partitions.forEach(p -> {
          long next = consumer.position(p, metadataTimeout);
          nextUncommitted.put(p, next);
          logger.info("Got an initial offset {}-{}-{} to start onupdate from", p.topic(), p.partition(), next);
        });
        consumer.seekToBeginning(partitions);
      }

    });

    consumer.poll(Duration.ofNanos(1)); // Do we need one poll for subscribe to happen?

    for (long n = 0; polls > 0 && n < polls; n++) {

      // According to "Detecting Consumer Failures" in https://kafka.apache.org/21/javadoc/index.html?org/apache/kafka/clients/consumer/KafkaConsumer.html
      // there seems to be need for a pause between polls (?)
      Thread.sleep(pollDuration.toMillis());

      ConsumerRecords<String, byte[]> polled = consumer.poll(pollDuration);
      int count = polled.count();
      logger.info("Polled {} records", count);

      if (nextUncommitted.isEmpty()) {
    	if (count > 0) throw new IllegalStateException("Received " + count + " records prior to an assigned partitions event");
        logger.info("Waiting for topic assignments");
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
          onupdate.handle(update, /*TODO*/ null);
        } else {
          logger.info("Suppressing onupdate for {} because start offset is {}", update, start);
        }
        // TODO deduplicate onupdate within the same poll/batch, by key
      }

      // TODO upon onupdate completion, but if ANY onpdate failed we should somehow abort the rest
      // and only commit to the last non-failing onupdate (using the Map arg call)
      consumer.commitSync();

      // Next poll ...
    }

  }

}
