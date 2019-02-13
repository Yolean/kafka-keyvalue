package se.yolean.kafka.keyvalue;

import java.util.Iterator;

import org.apache.kafka.streams.Topology;

public interface KeyvalueUpdate {

	Topology getTopology();

	byte[] getValue(String key);

  /**
   * @param topicName
   * @return offset for latest update, or null if the topic hasn't got updates
   */
  public Long getCurrentOffset(String topicName);

	Iterator<String> getKeys();

  Iterator<byte[]> getValues();

}
