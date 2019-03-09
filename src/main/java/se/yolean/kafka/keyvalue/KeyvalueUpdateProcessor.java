package se.yolean.kafka.keyvalue;

import java.time.Duration;
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
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.Punctuator;
import org.apache.kafka.streams.processor.StateStore;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

public class KeyvalueUpdateProcessor implements KeyvalueUpdate, Processor<String, byte[]> {

  static final Gauge onupdatePending = Gauge.build()
      .name("kkv_onupdate_pending").help("On-update instances created but not marked completed").register();
  static final Counter onupdateCompleted = Counter.build()
      .name("kkv_onupdate_completed").help("Total on-update requests completed (after retries)").register();
  static final Counter onupdateCompletedOutOfOrder = Counter.build()
      .name("kkv_onupdate_completed_outoforder").help("On-update requests completed out of order with previous").register();
  static final Counter onupdateSucceeded = Counter.build()
      .name("kkv_onupdate_succeeded").help("Total on-update requests succeeded (after retries)").register();
  static final Counter onupdateFailed = Counter.build()
      .name("kkv_onupdate_failed").help("Total on-update requests failed (after retries)").register();
  static final Counter offsetsNotProcessed = Counter.build()
      .name("kkv_offsets_not_processed").help("The processor won't see null key messages, so we count gaps in the offset sequence"
          + ". But note that kafka offsets are not guaranteed to be sequencial: https://stackoverflow.com/a/54637004").register();
  static final Gauge keyCount = Gauge.build()
      .name("kkv_keys").help("Total number of keys").register();

  private static final String SOURCE_NAME = "Source";
  private static final String PROCESSOR_NAME = "KeyvalueUpdate";
  private static final String STATE_STORE_NAME = "Keyvalue";

  public static final Logger logger = LogManager.getLogger(KeyvalueUpdateProcessor.class);

	private String sourceTopicPattern;
  private OnUpdate onUpdate;
  private ProcessorContext context = null;

  // We can't use this to build so we keep it for sanity checks
  private StoreBuilder<KeyValueStore<String, byte[]>> storeBuilder = null;

  private KeyValueStore<String, byte[]> store = null;

  private final Map<TopicPartition,Long> currentOffset = new HashMap<>(1);

  private final Map<TopicPartition,OnUpdateCompletionLogging> latestPending = new HashMap<>(1);

  private Maintenance maintenance;

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
    this.maintenance = new Maintenance();
    context.schedule(maintenance.getInterval(), PunctuationType.WALL_CLOCK_TIME, maintenance);
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
    OnUpdateCompletionLogging previous = latestPending.get(update.getTopicPartition());
    OnUpdateCompletionLogging completion = new OnUpdateCompletionLogging(update, previous);
    onUpdate.handle(update, completion);
    latestPending.put(update.getTopicPartition(), completion);
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

  private class Maintenance implements Punctuator {

    final Duration interval = Duration.ofSeconds(10);

    @Override
    public void punctuate(long timestamp) {
      keyCount.set(store.approximateNumEntries());
    }

    public Duration getInterval() {
      return interval;
    }

  }

  private static class OnUpdateCompletionLogging implements OnUpdate.Completion {

    private UpdateRecord record;
    private OnUpdateCompletionLogging previous;

    private boolean completed = false;

    OnUpdateCompletionLogging(UpdateRecord record, OnUpdateCompletionLogging previous) {
      this.record = record;
      if (previous == null) {
        logger.info("This is the first on-update for {}-{}", record.getTopicPartition(), record.getOffset());
      } else {
        logger.debug("Got onupdate completion for {}-{} previous offset {}", record.getTopicPartition(), record.getOffset(), previous.record.getOffset());
        // sanity checks here and the whole previous tracking can probably be removed once we have decent e2e coverage
        this.previous = previous;
        UpdateRecord p = previous.record;
        if (!record.getTopic().equals(p.getTopic())) throw new IllegalArgumentException("Mismatch with previous, topics: " + record.getTopic() + " != " + p.getTopic());
        if (record.getPartition() != p.getPartition()) throw new IllegalArgumentException("Mismatch with previous, topic " + record.getTopic() + " partitions: " + record.getPartition() + "!=" + p.getPartition());
        long offsetoffset = record.getOffset() - p.getOffset();
        if (offsetoffset == 0) throw new IllegalArgumentException("Duplicate completion logging for topic " + record.getTopic() + " partition " + record.getPartition() + " offset " + p.getOffset());
        if (offsetoffset < 0) throw new IllegalArgumentException("Completion tracking created in reverse offset order, topic " + record.getTopic() + " partition " + record.getPartition() + ": from " + p.getOffset() + " to " + record.getOffset());
        // null keys will be ignored so there might be gaps, but we should be able to create these logging instances in offset order
        if (offsetoffset > 1) offsetsNotProcessed.inc(offsetoffset);
      }
      onupdatePending.inc();
    }

    void onAny() {
      if (completed) {
        throw new IllegalArgumentException("Got on-update completion for already completed " + record);
      }
      completed = true;
      if (previous == null) {
        logger.info("Completed the first on-update for {}", record.getTopicPartition());
      } else if (!previous.completed) {
        onupdateCompletedOutOfOrder.inc();
        logger.warn("On-update completed out of order, {}-{} before {}",
            record.getTopicPartition(), record.getOffset(), previous.record.getOffset());
      }
      // let it be garbage collected
      previous = null;
      onupdatePending.dec();
      onupdateCompleted.inc();
    }

    @Override
    public void onSuccess() {
      onAny();
      logger.debug("On-update completed for {}", record);
      onupdateSucceeded.inc();
    }

    @Override
    public void onFailure() {
      onAny();
      logger.warn("On-update failed for {}", record);
      onupdateFailed.inc();
    }

  }

}
