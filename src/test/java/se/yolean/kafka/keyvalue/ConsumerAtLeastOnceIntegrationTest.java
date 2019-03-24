package se.yolean.kafka.keyvalue;

import java.util.Properties;
import java.util.concurrent.ExecutionException;

import com.github.charithe.kafka.KafkaHelper;
import com.github.charithe.kafka.KafkaJunitExtension;
import com.github.charithe.kafka.KafkaJunitExtensionConfig;
import com.github.charithe.kafka.StartupMode;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;

@ExtendWith(KafkaJunitExtension.class)
@KafkaJunitExtensionConfig(startupMode = StartupMode.WAIT_FOR_STARTUP)
public class ConsumerAtLeastOnceIntegrationTest {

  Properties getConsumerProperties(String bootstrap, String groupId) {
    Properties props = new Properties();
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    return props;
  }

  @Test
  void testSingleRun(KafkaHelper kafkaHelper) throws InterruptedException, ExecutionException {
    ConsumerAtLeastOnce consumer = new ConsumerAtLeastOnce();
    final String TOPIC = "testx";
    consumer.consumerProps = getConsumerProperties("localhost:" + kafkaHelper.kafkaPort(), "testgroup1");
    consumer.onupdate = Mockito.mock(OnUpdate.class);
    consumer.cache = new HashMap<>();
    consumer.topics = Collections.singleton(TOPIC);

    consumer.metadataTimeout = Duration.ofSeconds(1);
    consumer.pollDuration = Duration.ofMillis(100);

    KafkaProducer<String, byte[]> producer = kafkaHelper.createProducer(new StringSerializer(),
        new ByteArraySerializer(), new Properties());
    producer.send(new ProducerRecord<String,byte[]>(TOPIC, "k1", "v1".getBytes())).get();
    producer.send(new ProducerRecord<String,byte[]>(TOPIC, "k2", "v1".getBytes())).get();

    consumer.run();
    assertEquals(2, consumer.cache.size(), "Should have consumed two records with different key");
    assertTrue(consumer.cache.containsKey("k1"), "Should contain the first key");


    consumer.run();
  }


}