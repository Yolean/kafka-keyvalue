package se.yolean.kafka.keyvalue.onupdate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Properties;
import java.util.Random;

import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.test.ConsumerRecordFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import se.yolean.kafka.keyvalue.CacheServiceOptions;
import se.yolean.kafka.keyvalue.KeyvalueUpdate;
import se.yolean.kafka.keyvalue.KeyvalueUpdateProcessor;
import se.yolean.kafka.keyvalue.OnUpdate;
import se.yolean.kafka.keyvalue.http.CacheServer;
import se.yolean.kafka.keyvalue.http.ConfigureRest;

public class OnUpdateIntegrationTest {

  public static final String UPDATES_ENDPOINT_PATH = "/updates";

  private TopologyTestDriver testDriver;
  private ConsumerRecordFactory<String, String> recordFactory = new ConsumerRecordFactory<>(new StringSerializer(),
      new StringSerializer());

  private static final String TOPIC1 = "topic1";
  private KeyvalueUpdate cache = null;

  private CacheServer server = null;
  private int port;
  private String root;

  @BeforeEach
  public void setup() {
    Random random = new Random();
    port = 58000 + random.nextInt(1000);
    root = "http://127.0.0.1:" + port;

    OnUpdate onUpdate = OnUpdateFactory.getInstance().fromUrl(root + UPDATES_ENDPOINT_PATH);

    cache = new KeyvalueUpdateProcessor(TOPIC1, onUpdate);

    Properties config = new Properties();
    config.setProperty(StreamsConfig.APPLICATION_ID_CONFIG, "test-kafka-keyvalue");
    config.setProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

    Topology topology = cache.getTopology();

    testDriver = new TopologyTestDriver(topology, config);
  }

  @AfterEach
  void teardown() throws Exception {
    testDriver.close();

    if (server != null) {
      server.stop();
    }
    server = null;
  }

  @Test
  void testBasicFlow() throws InterruptedException, IOException {
    CacheServiceOptions options = Mockito.mock(CacheServiceOptions.class);
    Mockito.when(options.getPort()).thenReturn(port);

    UpdatesServlet updatesServlet = new UpdatesServlet();

    server = new ConfigureRest()
        .createContext(options.getPort(), "/")
        .asServlet()
        .addCustomServlet(updatesServlet, UPDATES_ENDPOINT_PATH)
        .create();
    server.start();

    assertEquals(0, updatesServlet.posts.size());

    testDriver.pipeInput(recordFactory.create(TOPIC1, "k1", "v1"));

    Thread.sleep(1000);
    assertEquals(1, updatesServlet.posts.size());

    assertEquals(1, updatesServlet.payloads.size());
    assertEquals("{\"topic\":\"topic1\",\"partition\":0,\"offset\":0,\"key\":\"k1\"}", updatesServlet.payloads.get(0));
  }

}
