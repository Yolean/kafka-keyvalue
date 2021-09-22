package se.yolean.kafka.keyvalue;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.record.CompressionType;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;

import com.salesforce.kafka.test.KafkaBroker;
import com.salesforce.kafka.test.junit5.SharedKafkaTestResource;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import se.yolean.kafka.keyvalue.ConsumerAtLeastOnce.Stage;

public class ErrorHandlingKafkaIntegrationTest {

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
  void testBrokerDisconnect() throws Exception {

    ConsumerAtLeastOnce consumer = new ConsumerAtLeastOnce(new SimpleMeterRegistry());
    final String TOPIC = "topicx";
    final String GROUP = this.getClass().getSimpleName() + "_testBrokerDisconnect_" + System.currentTimeMillis();
    final String BOOTSTRAP = kafka.getKafkaConnectString();
    kafka.getKafkaTestUtils().createTopic("topicx", 3, (short) 1);

    consumer.consumerProps = getConsumerProperties(BOOTSTRAP, GROUP);
    consumer.onupdate = Mockito.mock(OnUpdate.class);
    consumer.cache = new HashMap<>();
    consumer.topics = Collections.singletonList(TOPIC);

    consumer.maxPolls = 5;
    consumer.metadataTimeout = Duration.ofMillis(2000);
    consumer.pollDuration = Duration.ofMillis(100);
    consumer.minPauseBetweenPolls = Duration.ofMillis(100);

    KafkaProducer<String, byte[]> producer = new KafkaProducer<>(getTestProducerProperties(BOOTSTRAP));
    producer.send(new ProducerRecord<String,byte[]>(TOPIC, "k1", "v1".getBytes())).get();

    consumer.consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest"); // start from scratch even if we're reusing a test topic
    consumer.run();
    consumer.consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "none"); // the test should fail if we don't have an offset after the first run

    assertEquals(Stage.Polling, consumer.stage); // To be able to see where we exited we're not resetting stage at the end of runs

    assertEquals(1, consumer.cache.size(), "Should be operational now, before we mess with connections");
    assertEquals("v1", new String(consumer.cache.get("k1")), "Should have the first key's value");

    KafkaBroker broker = kafka.getKafkaBrokers().iterator().next();

    broker.stop();
    try {
      consumer.run();
    } catch (Exception e) {
      e.printStackTrace(); // Oddly we don't get a stack trace
      assertEquals(org.apache.kafka.common.errors.TimeoutException.class, e.getClass());
    }

    broker.start();
    producer.send(new ProducerRecord<String,byte[]>(TOPIC, "k2", "v1".getBytes())).get();
    consumer.run();

    assertEquals(2, consumer.cache.size(), "Cache should be operational again, after the broker was restarted");
    assertEquals("v1", new String(consumer.cache.get("k2")), "Should have the first key's value");

    producer.close();
  }

}
