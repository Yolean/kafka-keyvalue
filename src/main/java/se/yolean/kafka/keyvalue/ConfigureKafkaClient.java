package se.yolean.kafka.keyvalue;

import java.util.Properties;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Provider;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Note that KKV uses the consumer offset to learn, upon restarts,
 * when to begin triggering onupdate events to targets.
 *
 * It will always consume from start of topics.
 */
@ApplicationScoped
public class ConfigureKafkaClient implements Provider<Properties> {

  public static final String DEFAULT_OFFSET_RESET = "none";

  @ConfigProperty(name="kafka_bootstrap")
  String bootstrap;

  @ConfigProperty(name="kafka_group_id")
  String groupId;

  @ConfigProperty(name="kafka_offset_reset", defaultValue=DEFAULT_OFFSET_RESET)
  String offsetReset;

  @ConfigProperty(name="kafka_idempotence", defaultValue="true")
  boolean idempotence;

  Properties getConsumerProperties() {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, offsetReset);
    // Properties below are chosen to match the application's design and should not be altered
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    return props;
  }

  @Produces
  //@javax.inject.Named("consumer")
  @Override
  public Properties get() {
    Properties props = getConsumerProperties();
    return props;
  }

}
