package se.yolean.kafka.keyvalue;

import java.util.Iterator;

import org.apache.kafka.streams.Topology;

public interface KeyvalueUpdate {

	Topology getTopology();

	byte[] getValue(String key);

	Long getCurrentOffset();

	Iterator<String> getKeys();

  Iterator<byte[]> getValues();

}
