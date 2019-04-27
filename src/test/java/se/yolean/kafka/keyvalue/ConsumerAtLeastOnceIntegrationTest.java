package se.yolean.kafka.keyvalue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.record.CompressionType;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;

import com.salesforce.kafka.test.junit5.SharedKafkaTestResource;

public class ConsumerAtLeastOnceIntegrationTest {

  @RegisterExtension
  static final SharedKafkaTestResource kafka = new SharedKafkaTestResource().withBrokers(1);

  Properties getConsumerProperties(String bootstrap, String groupId) {
    Properties props = new Properties();
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    // use latest here to allow many test cases to use the same topic (though all test cases keys' will be read to cache)
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "none");
    return props;
  }

  Properties getTestProducerProperties(String bootstrap) {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, CompressionType.NONE.name);
    return props;
  }

  @Test
  void testSingleRun() throws InterruptedException, ExecutionException {

    ConsumerAtLeastOnce consumer = new ConsumerAtLeastOnce();
    final String TOPIC = "topic1";
    final String GROUP = this.getClass().getSimpleName() + "_testSingleRun_" + System.currentTimeMillis();
    final String BOOTSTRAP = kafka.getKafkaConnectString();
    kafka.getKafkaTestUtils().createTopic("topic1", 1, (short) 1);

    consumer.consumerProps = getConsumerProperties(BOOTSTRAP, GROUP);
    consumer.onupdate = Mockito.mock(OnUpdate.class);
    consumer.cache = new HashMap<>();
    consumer.topics = Collections.singletonList(TOPIC);

    consumer.maxPolls = 5;
    consumer.metadataTimeout = Duration.ofSeconds(10); // TODO tests fail on an assertion further down if this is too short, there's no produce error
    consumer.pollDuration = Duration.ofMillis(100);
    consumer.minPauseBetweenPolls = Duration.ofMillis(100);

    KafkaProducer<String, byte[]> producer = new KafkaProducer<>(getTestProducerProperties(BOOTSTRAP));
    producer.send(new ProducerRecord<String,byte[]>(TOPIC, "k1", "v1".getBytes())).get();
    producer.send(new ProducerRecord<String,byte[]>(TOPIC, "k2", "v1".getBytes())).get();

    // TODO assertFalse(consumer.isReady(), "Shouldn't be ready before subscribed to a topic(s)");

    consumer.consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest"); // start from scratch even if we're reusing a test topic
    consumer.run();
    consumer.consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "none"); // the test should fail if we don't have an offset after the first run

    assertEquals(null, consumer.call().getData().orElse(Collections.emptyMap()).get("error-message"));
    // TODO assertTrue(consumer.isReady(), "Should be ready now, after reading");

    assertEquals(2, consumer.cache.size(), "Should have consumed two records with different key");
    assertTrue(consumer.cache.containsKey("k1"), "Should contain the first key");
    assertEquals("v1", new String(consumer.cache.get("k1")), "Should have the first key's value");

    producer.send(new ProducerRecord<String,byte[]>(TOPIC, "k1", "v2".getBytes())).get();
    // TODO per-test kafka topic: producer.send(new ProducerRecord<String,byte[]>(TOPIC, "k3", "v2".getBytes())).get();
    assertEquals(2, consumer.cache.size(), "Nothing should happen unless run() is ongoing");

    consumer.run();
    // TODO per-test kafka topic: assertEquals(3, consumer.cache.size(), "Should have got the additional key from the last batch");
    assertEquals("v2", new String(consumer.cache.get("k1")), "Value should come from the latest record");

    Mockito.verify(consumer.onupdate).handle(new UpdateRecord(TOPIC, 0, 2, "k1"));

    assertEquals(null, consumer.call().getData().orElse(Collections.emptyMap()).get("error-message"));

    // API extended after this test was written. We should probably verify order too.
    Mockito.verify(consumer.onupdate, Mockito.atLeast(3)).pollStart(Collections.singletonList(TOPIC));
    Mockito.verify(consumer.onupdate, Mockito.atLeast(3)).pollEndBlockingUntilTargetsAck();

    producer.close();

    assertEquals(null, consumer.call().getData().orElse(Collections.emptyMap()).get("error-message"));

    // verify KafkaCache interface methods, as the REST resource uses that API
    KafkaCache cache = (KafkaCache) consumer;
    assertEquals("v1", new String(cache.getValue("k1")));

    // TODO assertEquals(1, cache.getCurrentOffset(TOPIC, 0));
    // TODO assertEquals(null, cache.getCurrentOffset(TOPIC, 1));

    // We originally required a deterministic iteration order but now it's upp to the map impl
    Iterator<String> keys = cache.getKeys();
    assertTrue(keys.hasNext());
    assertEquals("k1", keys.next());
    assertTrue(keys.hasNext());
    assertEquals("k2", keys.next());
    assertFalse(keys.hasNext());

    Iterator<byte[]> values = cache.getValues();
    assertTrue(values.hasNext());
    assertEquals("v2", new String(values.next()));
    assertFalse(values.hasNext());
  }

  @Test
  void testMetadataTimeout() throws InterruptedException, ExecutionException {

    ConsumerAtLeastOnce consumer = new ConsumerAtLeastOnce();
    final String TOPIC = "topic1";
    final String GROUP = this.getClass().getSimpleName() + "_testSingleRun_" + System.currentTimeMillis();
    final String BOOTSTRAP = kafka.getKafkaConnectString();
    kafka.getKafkaTestUtils().createTopic("topic1", 1, (short) 1);

    consumer.consumerProps = getConsumerProperties(BOOTSTRAP, GROUP);
    consumer.onupdate = Mockito.mock(OnUpdate.class);
    consumer.cache = new HashMap<>();
    consumer.topics = Collections.singletonList(TOPIC);

    consumer.maxPolls = 5;
    consumer.metadataTimeout = Duration.ofMillis(10);
    consumer.topicCheckRetries = 0; // With >0 retries we're likely to succeed
    consumer.pollDuration = Duration.ofMillis(1000);
    consumer.minPauseBetweenPolls = Duration.ofMillis(500);

    long t1 = System.currentTimeMillis();
    consumer.run();
    assertTrue(System.currentTimeMillis() - t1 > 20, "Should have spent time waiting for metadata timeout twice");
    assertTrue(System.currentTimeMillis() - t1 < 500, "Should have exited after metadata timeout, not waited for other things");

    assertEquals("Timeout expired while fetching topic metadata",
        consumer.call().getData().orElse(Collections.emptyMap()).get("error-message"),
        "Should have thrown when metadata failed");
  }

  @Test
  void testTopicNonexistent() throws InterruptedException, ExecutionException {

    ConsumerAtLeastOnce consumer = new ConsumerAtLeastOnce();
    final String TOPIC = "topic0";
    final String GROUP = this.getClass().getSimpleName() + "_testSingleRun_" + System.currentTimeMillis();
    final String BOOTSTRAP = kafka.getKafkaConnectString();

    consumer.consumerProps = getConsumerProperties(BOOTSTRAP, GROUP);
    consumer.onupdate = Mockito.mock(OnUpdate.class);
    consumer.cache = new HashMap<>();
    consumer.topics = Collections.singletonList(TOPIC);

    consumer.maxPolls = 5;
    consumer.metadataTimeout = Duration.ofMillis(10);
    consumer.topicCheckRetries = 5;
    consumer.pollDuration = Duration.ofMillis(1000);
    consumer.minPauseBetweenPolls = Duration.ofMillis(500);

    long t1 = System.currentTimeMillis();
    consumer.run();
    assertTrue(System.currentTimeMillis() - t1 > 50, "Should have spent time waiting for topic existence x5");
    assertTrue(System.currentTimeMillis() - t1 < 500, "Should have exited after these retries, not waited for other things");

    assertEquals("Gave up waiting for topic existence after 5 retries with 0s timeout",
        consumer.call().getData().orElse(Collections.emptyMap()).get("error-message"),
        "Should have thrown when metadata failed");
  }

  // TODO
  //@Test
  void testNoOffsetReset() {
    // auto.offset.reset=none should be the default and we should have good error handling for it
  }

}