package se.yolean.kafka.keyvalue;

import java.util.HashMap;
import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.providers.connectors.InMemoryConnector;

public class KafkaTestResourceLifecycleManager implements QuarkusTestResourceLifecycleManager {

  @Override
  public Map<String, String> start() {
    Map<String, String> env = new HashMap<>();
    Map<String, String> props1 = InMemoryConnector.switchIncomingChannelsToInMemory("topic");
    env.putAll(props1);
    return env;
  }

  @Override
  public void stop() {
    InMemoryConnector.clear();
  }

}
