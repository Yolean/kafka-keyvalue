package se.yolean.kafka.keyvalue;

import org.apache.kafka.streams.Topology;

public class KeyvalueUpdateProcessor implements KeyvalueUpdate {

	private String sourceTopicPattern;

  public KeyvalueUpdateProcessor(String sourceTopic) {
	  this.sourceTopicPattern = sourceTopic;
	}

	@Override
	public Topology getTopology() {
		Topology topology = new Topology();

		topology.addSource("Source", sourceTopicPattern);

		return topology;
	}

}
