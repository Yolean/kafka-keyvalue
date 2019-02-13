package se.yolean.kafka.keyvalue;

import java.util.Iterator;

import org.apache.kafka.streams.Topology;

public interface KeyvalueUpdate {

	Topology getTopology();

	byte[] getValue(byte[] key);

	Long getCurrentOffset();

	Iterator<byte[]> getKeys();

  Iterator<byte[]> getValues();

}
