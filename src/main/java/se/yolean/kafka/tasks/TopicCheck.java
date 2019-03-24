package se.yolean.kafka.tasks;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Copied from Yolean/kafka-topics-copy, pending real reuse.
 */
public class TopicCheck implements Runnable {

  final Logger logger = LoggerFactory.getLogger(TopicCheck.class);

  Create create;
  List<String> sourceTopics;
  Duration timeout;

  boolean sourceTopicsExist;

  public TopicCheck(Create create, List<String> sourceTopics, Duration listTopicsTimeout) {
    this.create = create;
    this.sourceTopics = sourceTopics;
    this.timeout = listTopicsTimeout;
  }

  @Override
  public void run() {
    this.sourceTopicsExist = consumerSeesTopics(create.getConsumer(), sourceTopics, timeout );
  }

  boolean consumerSeesTopics(KafkaConsumer<? extends Object, ? extends Object> consumer, Collection<String> sourceTopics, Duration timeout) {
    Map<String, List<PartitionInfo>> topics = consumer.listTopics(timeout);
    if (topics == null) {
      logger.warn("Got null topics list from consumer");
      return false;
    }
    if (topics.size() == 0) {
      logger.info("Zero topics found on the consumer side");
      return false;
    }
    for (String t : sourceTopics) {
      if (!topics.containsKey(t)) {
        logger.info("Topic {} not found at source", t);
        return false;
      }
    }
    return true;
  }

  public boolean sourceTopicsExist() {
    return this.sourceTopicsExist;
  }

}
