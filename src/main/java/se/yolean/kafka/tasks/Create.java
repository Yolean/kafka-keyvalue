package se.yolean.kafka.tasks;

import org.apache.kafka.clients.consumer.KafkaConsumer;

/**
 * Stub Create, to be able to reuse TopicCheck from kafka-topics-copy
 */
public abstract class Create {

  public abstract KafkaConsumer<? extends Object, ? extends Object> getConsumer();

}