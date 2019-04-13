package se.yolean.kafka.keyvalue;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;

public class ConsumerAtLeastOnceIntegrationTest {

  @RegisterExtension
  static final SharedKafkaTestResource kafka = new SharedKafkaTestResource().withBrokers(2);

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
    consumer.topics = Collections.singleton(TOPIC);

    consumer.maxPolls = 5;
    consumer.metadataTimeout = Duration.ofSeconds(1);
    consumer.pollDuration = Duration.ofMillis(100);

    KafkaProducer<String, byte[]> producer = new KafkaProducer<>(getTestProducerProperties(BOOTSTRAP));
    producer.send(new ProducerRecord<String,byte[]>(TOPIC, "k1", "v1".getBytes())).get();
    producer.send(new ProducerRecord<String,byte[]>(TOPIC, "k2", "v1".getBytes())).get();

    consumer.consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest"); // start from scratch even if we're reusing a test topic
    consumer.run();
    consumer.consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "none"); // the test should fail if we don't have an offset after the first run

    assertEquals(2, consumer.cache.size(), "Should have consumed two records with different key");
    assertTrue(consumer.cache.containsKey("k1"), "Should contain the first key");
    assertEquals("v1", new String(consumer.cache.get("k1")), "Should have the first key's value");

    producer.send(new ProducerRecord<String,byte[]>(TOPIC, "k1", "v2".getBytes())).get();
    // TODO per-test kafka topic: producer.send(new ProducerRecord<String,byte[]>(TOPIC, "k3", "v2".getBytes())).get();
    assertEquals(2, consumer.cache.size(), "Nothing should happen unless run() is ongoing");
    
    consumer.run();
    // TODO per-test kafka topic: assertEquals(3, consumer.cache.size(), "Should have got the additional key from the last batch");
    assertEquals("v2", new String(consumer.cache.get("k1")), "Value should come from the latest record");

    Mockito.verify(consumer.onupdate).handle(new UpdateRecord(TOPIC, 0, 2, "k1"), null);

    producer.close();
  }

}