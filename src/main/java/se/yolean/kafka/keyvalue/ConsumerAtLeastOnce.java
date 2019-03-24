package se.yolean.kafka.keyvalue;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
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

/**
 *
 */
public class ConsumerAtLeastOnce implements Runnable {

  static final Logger logger = LoggerFactory.getLogger(ConsumerAtLeastOnce.class);

  Properties consumerProps;

  Collection<String> topics;

  Duration metadataTimeout;

  Duration pollDuration;

  long maxPolls = 100;

  OnUpdate onupdate;

  Map<String, byte[]> cache;

  @Override
  public void run() {
    try {
      runThatThrows();
    } catch (InterruptedException e) {
      throw new IllegalStateException("No error handling for this error", e);
    }
  }

  void runThatThrows() throws InterruptedException {
    logger.info("Running");

    KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(consumerProps);

    final Map<TopicPartition, Long> nextUncommitted = new HashMap<>(1);

    consumer.subscribe(topics, new ConsumerRebalanceListener() {

      @Override
      public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
        logger.info("Got revoked {}", partitions);
      }

      @Override
      public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
        partitions.forEach(p -> {
          long next = consumer.position(p, metadataTimeout);
          nextUncommitted.put(p, next);
          logger.info("Got an initial offset {}-{}-{} to start onupdate from", p.topic(), p.partition(), next);
        });
        consumer.seekToBeginning(partitions);
      }

    });

    logger.info("Subscribed to {}", nextUncommitted.keySet());

    for (long n = 0; maxPolls > 0 && n < maxPolls; n++) {
      if (nextUncommitted.isEmpty()) {
        logger.info("Waiting for topic assignments");
        Thread.sleep(pollDuration.toMillis());
        continue;
      }

      ConsumerRecords<String, byte[]> polled = consumer.poll(pollDuration);
      int count = polled.count();
      logger.info("Polled {} records", count);
      Iterator<ConsumerRecord<String, byte[]>> records = polled.iterator();
      while (records.hasNext()) {
        ConsumerRecord<String, byte[]> record = records.next();
        cache.put(record.key(), record.value());
      }
    }
  }



}
