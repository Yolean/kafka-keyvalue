package se.yolean.kafka.keyvalue;

import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class KafkaPollListener implements ConsumerInterceptor<String, byte[]> {

  private static final Logger logger = LoggerFactory.getLogger(KafkaPollListener.class);

  private static KafkaPollListener singleton = null;

  public static boolean getIsPollEndOnce() {
    if (!singleton.pollEnded) return false;
    singleton.pollEnded = false;
    logger.info("poll end true, returned once");
    return true;
  }

  private boolean pollEnded = false;

  public KafkaPollListener() {
    if (singleton != null) {
      throw new IllegalStateException("Poll listener has already been instantiated");
    }
    singleton = this;
  }

  @Override
  public void configure(Map<String, ?> configs) {
    logger.info("Poll listener started");
  }

  @Override
  public ConsumerRecords<String, byte[]> onConsume(ConsumerRecords<String, byte[]> records) {
    logger.info("onConsume message count {}", records.count());
    this.pollEnded = true;
    return records;
  }

  @Override
  public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {
    offsets.forEach((tp, offset) -> logger.info("onCommmit {} {}", tp, offset));
  }

  @Override
  public void close() {
  }

}
