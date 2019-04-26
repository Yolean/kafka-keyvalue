package se.yolean.kafka.tasks;

import org.apache.kafka.clients.consumer.KafkaConsumer;

/**
 * Stub Create, to be able to reuse TopicCheck from kafka-topics-copy
 */
public class Create {

  private KafkaConsumer<? extends Object, ? extends Object> consumer;

  public Create(KafkaConsumer<? extends Object, ? extends Object> alreadyCreated) {
    this.consumer = alreadyCreated;
  }

  public KafkaConsumer<? extends Object, ? extends Object> getConsumer() {
    return this.consumer;
  }

}