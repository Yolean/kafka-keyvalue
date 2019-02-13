package se.yolean.kafka.keyvalue;

import java.util.Iterator;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeyvalueUpdateProcessor implements KeyvalueUpdate, Processor<byte[], byte[]> {

  public static final Logger logger = LoggerFactory.getLogger(KeyvalueUpdateProcessor.class);

	private String sourceTopicPattern;
  private OnUpdate onUpdate;
  private ProcessorContext context;

  private KeyValueStore<byte[], byte[]> store;

  public KeyvalueUpdateProcessor(String sourceTopic, OnUpdate onUpdate) {
	  this.sourceTopicPattern = sourceTopic;
	  this.onUpdate = onUpdate;
	}

  private KeyValueStore<byte[], byte[]> getStateStore() {
    StoreBuilder<KeyValueStore<byte[], byte[]>> storeBuilder = Stores.keyValueStoreBuilder(
      Stores.inMemoryKeyValueStore("Keyvalue"),
      Serdes.ByteArray(),
      Serdes.ByteArray()
    );
    return storeBuilder.build();
  }

	@Override
	public Topology getTopology() {
    if (this.store != null) {
      throw new IllegalStateException("This processor instance has already created a topology");
    }
    this.store = getStateStore();

    Topology topology = new Topology();

		topology.addSource("Source", sourceTopicPattern);

		topology.addProcessor("KeyvalueUpdate", () -> this, "Source");

		return topology;
	}

  @Override
  public void init(ProcessorContext context) {
    this.context = context;
  }

  @Override
  public void process(byte[] key, byte[] value) {
    logger.debug("Got keyvalue {}={}", new String(key), new String(value));
    store.put(key, value);
  }

  @Override
  public void close() {
    // TODO Auto-generated method stub

  }

  @Override
  public byte[] getValue(byte[] key) {
    return store.get(key);
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
