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

  @BeforeEach
  public void setup() {
    cache = new KeyvalueUpdateProcessor(TOPIC1);

    Properties config = new Properties();
    config.setProperty(StreamsConfig.APPLICATION_ID_CONFIG, "testBoardStart");
    config.setProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

    Topology topology = cache.getTopology();

    testDriver = new TopologyTestDriver(topology, config);
  }

	@Test
	void test() {
		testDriver.pipeInput(recordFactory.create(TOPIC1, "k1", "v1"));
	}

}
