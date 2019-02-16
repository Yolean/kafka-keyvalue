package se.yolean.kafka.keyvalue;

import java.util.Iterator;

public interface KafkaCache {

  /**
   * @return false if anything on the processor side indicates unreadiness
   * @see Readiness#isStreamsReady()
   */
  boolean isReady();

  byte[] getValue(String key);

  /**
   * @param topicName
   * @param partition
   * @return offset for latest update, or null if the topic hasn't got updates
   */
  Long getCurrentOffset(String topicName, int partition);

  Iterator<String> getKeys();

  Iterator<byte[]> getValues();

}