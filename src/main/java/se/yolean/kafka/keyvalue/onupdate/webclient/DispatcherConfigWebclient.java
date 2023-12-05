package se.yolean.kafka.keyvalue.onupdate.webclient;

import jakarta.enterprise.context.ApplicationScoped;
import se.yolean.kafka.keyvalue.onupdate.DispatcherConfig;
import se.yolean.kafka.keyvalue.onupdate.UpdatesBodyPerTopic;
import se.yolean.kafka.keyvalue.onupdate.UpdatesBodyPerTopicJSON;

@ApplicationScoped
public class DispatcherConfigWebclient implements DispatcherConfig {

  @Override
  public UpdatesBodyPerTopic getUpdatesHandlerForPoll(String topic) {
    return new UpdatesBodyPerTopicJSON(topic);
  }

}
