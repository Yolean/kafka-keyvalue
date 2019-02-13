package se.yolean.kafka.keyvalue;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Properties;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.test.ConsumerRecordFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeyvalueUpdateIntegrationTest {

	private TopologyTestDriver testDriver;
  private ConsumerRecordFactory<String, String> recordFactory =
      new ConsumerRecordFactory<>(new StringSerializer(), new StringSerializer());

	private static final String TOPIC1 = "topic1";
	private KeyvalueUpdate cache = null;
	private OnUpdateRecordInMemory onUpdate = new OnUpdateRecordInMemory();

  @BeforeEach
  public void setup() {
    cache = new KeyvalueUpdateProcessor(TOPIC1, onUpdate);

    Properties config = new Properties();
    config.setProperty(StreamsConfig.APPLICATION_ID_CONFIG, "test-kafka-keyvalue");
    config.setProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

    Topology topology = cache.getTopology();

    testDriver = new TopologyTestDriver(topology, config);
  }

	@Test
	void testBasicFlow() {
		testDriver.pipeInput(recordFactory.create(TOPIC1, "k1", "v1"));

		assertEquals(null, cache.getValue("k0".getBytes()));

		byte[] v1 = cache.getValue("k1".getBytes());
		assertNotNull(v1);
		assertEquals("v1", new String(v1));
		assertEquals(1, onUpdate.getAll().size());

		UpdateRecord update = onUpdate.getAll().get(0);
		assertEquals(TOPIC1, update.getTopic());
		assertEquals(0, update.getPartition());
		assertEquals(0, update.getOffset());
		assertEquals("k1", new String(update.getKey()));
	}

}
