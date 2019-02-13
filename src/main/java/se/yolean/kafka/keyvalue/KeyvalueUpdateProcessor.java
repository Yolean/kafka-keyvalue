package se.yolean.kafka.keyvalue;

import java.util.Iterator;

import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;

public class KeyvalueUpdateProcessor implements KeyvalueUpdate, Processor<byte[], byte[]> {

	private String sourceTopicPattern;
  private OnUpdate onUpdate;
  private ProcessorContext context;

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

  @Override
  public void init(ProcessorContext context) {
    this.context = context;
  }

  @Override
  public void process(byte[] key, byte[] value) {
    // TODO Auto-generated method stub

  }

  @Override
  public void close() {
    // TODO Auto-generated method stub

  }

}
