package se.yolean.kafka.keyvalue;

import java.util.Iterator;

import org.apache.kafka.streams.Topology;

public interface KeyvalueUpdate {

  /**
   * Meant to be used with Kubernetes readiness probes to block use of the cache
   * during startup whey it may lag behind the topic.
   * Or if there's any other reason to suspect that the cache is unreliable.
   *
   * @return true if the cache can be considered up-to-date
   */
  boolean isReady();

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
