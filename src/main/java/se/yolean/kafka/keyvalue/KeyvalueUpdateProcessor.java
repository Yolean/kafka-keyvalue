package se.yolean.kafka.keyvalue;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.StateStore;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeyvalueUpdateProcessor implements KeyvalueUpdate, Processor<String, byte[]> {

  private static final String SOURCE_NAME = "Source";
  private static final String PROCESSOR_NAME = "KeyvalueUpdate";
  private static final String STATE_STORE_NAME = "Keyvalue";

  public static final Logger logger = LoggerFactory.getLogger(KeyvalueUpdateProcessor.class);

	private String sourceTopicPattern;
  private OnUpdate onUpdate;
  private ProcessorContext context = null;

  // We can't use this to build so we keep it for sanity checks
  private StoreBuilder<KeyValueStore<String, byte[]>> storeBuilder = null;

  private KeyValueStore<String, byte[]> store = null;

  private final Map<TopicPartition,Long> currentOffset = new HashMap<TopicPartition,Long>(1);

  // Not sure yet if we want to construct these objects for every update
  private final Runnable onUpdateCompletion = new Runnable() {
    @Override
    public void run() {
      logger.trace("onupdate completion ignored");
    }
  };

  public KeyvalueUpdateProcessor(String sourceTopic, OnUpdate onUpdate) {
	  this.sourceTopicPattern = sourceTopic;
	  this.onUpdate = onUpdate;
	}

  private void configureStateStore(String name) {
    storeBuilder = Stores.keyValueStoreBuilder(
      Stores.inMemoryKeyValueStore(name),
      Serdes.String(),
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
		  .addSource(SOURCE_NAME,
		      new StringDeserializer(),
		      new ByteArrayDeserializer(),
		      sourceTopicPattern)
		  .addProcessor(PROCESSOR_NAME, () -> this, SOURCE_NAME)
		  .addStateStore(storeBuilder, PROCESSOR_NAME);

		logger.info(topology.describe().toString());

		return topology;
	}

  @Override
  public void init(ProcessorContext context) {
    logger.info("Init applicationId={}", context.applicationId());
    keepStateStore(context);
    this.context = context;
  }

  @Override
  public void close() {
    store.close();
  }

  @Override
  public boolean isReady() {
    if (context == null) {
      return false;
    }
    // we should add more unreadiness criterias here if we can find any
    return true;
  }

  @SuppressWarnings("unchecked")
  private void keepStateStore(ProcessorContext context) {
    StateStore stateStore = context.getStateStore(STATE_STORE_NAME);
    logger.info("Found store {} open={}, persistent={}", stateStore.name(), stateStore.isOpen(), stateStore.persistent());
    this.store = (KeyValueStore<String, byte[]>) stateStore;
  }

  @Override
  public void process(String key, byte[] value) {
    logger.trace("Got keyvalue {}={}", key, new String(value));
    UpdateRecord update = new UpdateRecord(context.topic(), context.partition(), context.offset(), key);
    if (key == null) {
      logger.debug("Ignoring null key at " + update);
      return;
    }
    if (key.length() == 0) {
      logger.debug("Ignoring zero-length key at " + update);
      return;
    }
    process(update, value);
    currentOffset.put(update.getTopicPartition(), update.getOffset());
  }

  private void process(UpdateRecord update, byte[] value) {
    store.put(update.getKey(), value);
    onUpdate.handle(update, onUpdateCompletion);
  }

  @Override
  public byte[] getValue(String key) {
    if (key == null) {
      throw new IllegalArgumentException("Key can not be null because such messages are ignored at cache update");
    }
    if (key.length() == 0) {
      throw new IllegalArgumentException("Empty string key is disallowed to avoid surprises");
    }
    return store.get(key);
  }

  @Override
  public Long getCurrentOffset(String topicName, int partition) {
    return currentOffset.get(new TopicPartition(topicName, partition));
  }

  @Override
  public Iterator<String> getKeys() {
    final Iterator<KeyValue<String, byte[]>> all = store.all();
    return new Iterator<String>() {


      @Override
      public boolean hasNext() {
        return all.hasNext();
      }

      @Override
      public String next() {
        return all.next().key;
      }

    };
  }

  @Override
  public Iterator<byte[]> getValues() {
    final Iterator<KeyValue<String, byte[]>> all = store.all();
    return new Iterator<byte[]>() {


      @Override
      public boolean hasNext() {
        return all.hasNext();
      }

      @Override
      public byte[] next() {
        return all.next().value;
      }

    };
  }

}
