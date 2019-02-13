package se.yolean.kafka.keyvalue;

import java.util.Iterator;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.StateStore;
import org.apache.kafka.streams.processor.To;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeyvalueUpdateProcessor implements KeyvalueUpdate, Processor<byte[], byte[]> {

  private static final String SOURCE_NAME = "Source";
  private static final String PROCESSOR_NAME = "KeyvalueUpdate";
  private static final String STATE_STORE_NAME = "Keyvalue";

  public static final Logger logger = LoggerFactory.getLogger(KeyvalueUpdateProcessor.class);

	private String sourceTopicPattern;
  private OnUpdate onUpdate;
  private ProcessorContext context;

  // We can't use this to build so we keep it for sanity checks
  private StoreBuilder<KeyValueStore<byte[], byte[]>> storeBuilder = null;

  private KeyValueStore<byte[], byte[]> store = null;

  public KeyvalueUpdateProcessor(String sourceTopic, OnUpdate onUpdate) {
	  this.sourceTopicPattern = sourceTopic;
	  this.onUpdate = onUpdate;
	}

  private void configureStateStore(String name) {
    storeBuilder = Stores.keyValueStoreBuilder(
      Stores.inMemoryKeyValueStore(name),
      Serdes.ByteArray(),
      Serdes.ByteArray()
    );
  }

	@Override
	public Topology getTopology() {
    if (this.storeBuilder != null) {
      throw new IllegalStateException("This processor instance has already created a topology");
    }
    configureStateStore(STATE_STORE_NAME);

    Topology topology = new Topology();

		topology
		  .addSource(SOURCE_NAME, sourceTopicPattern)
		  .addProcessor(PROCESSOR_NAME, () -> this, SOURCE_NAME)
		  .addStateStore(storeBuilder, PROCESSOR_NAME);

		return topology;
	}

  @Override
  public void init(ProcessorContext context) {
    logger.info("Init applicationId={}", context.applicationId());
    StateStore stateStore = context.getStateStore(STATE_STORE_NAME);
    logger.info("Found store {} open={}, persistent={}", stateStore.name(), stateStore.isOpen(), stateStore.persistent());
    this.store = (KeyValueStore<byte[], byte[]>) stateStore;
    this.context = context;
  }

  @Override
  public void process(byte[] key, byte[] value) {
    logger.debug("Got keyvalue {}={}", new String(key), new String(value));
    //store.put(key, value);
    context.forward(key, value, To.all());
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
