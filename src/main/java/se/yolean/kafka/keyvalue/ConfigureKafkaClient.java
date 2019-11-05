// Copyright 2019 Yolean AB
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package se.yolean.kafka.keyvalue;

import java.util.Optional;
import java.util.Properties;

import javax.enterprise.inject.Produces;
import javax.inject.Provider;
import javax.inject.Singleton;

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
@Singleton
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

  @ConfigProperty(name="kafka_max_poll_records")
  Optional<Integer> maxPollRecords;

  Properties getConsumerProperties() {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, offsetReset);
    // Properties below are chosen to match the application's design and should not be altered
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    if (maxPollRecords.isPresent()) {
      props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords.get());
    }
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
