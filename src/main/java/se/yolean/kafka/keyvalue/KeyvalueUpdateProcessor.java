package se.yolean.kafka.keyvalue;

import java.util.Iterator;

import org.apache.kafka.streams.Topology;

public class KeyvalueUpdateProcessor implements KeyvalueUpdate {

	private String sourceTopicPattern;
  private OnUpdate onUpdate;

  public KeyvalueUpdateProcessor(String sourceTopic, OnUpdate onUpdate) {
	  this.sourceTopicPattern = sourceTopic;
	  this.onUpdate = onUpdate;
	}

	@Override
	public Topology getTopology() {
		Topology topology = new Topology();

		topology.addSource("Source", sourceTopicPattern);

		return topology;
	}

  @Override
  public byte[] getValue(byte[] key) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Long getCurrentOffset() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Iterator<byte[]> getAllKeys() {
    // TODO Auto-generated method stub
    return null;
  }

}
