package se.yolean.kafka.keyvalue.onupdate.hc;

import javax.inject.Singleton;

import se.yolean.kafka.keyvalue.onupdate.DispatcherConfig;
import se.yolean.kafka.keyvalue.onupdate.UpdatesBodyPerTopic;
import se.yolean.kafka.keyvalue.onupdate.UpdatesBodyPerTopicJSON;
import se.yolean.kafka.keyvalue.onupdate.UpdatesDispatcher;

@Singleton
public class DispatcherConfigHttpclient implements DispatcherConfig {

  //@ConfigProperty(name="update_connection_timeout", defaultValue="1")
  //Duration connectionTimeout;

  @Override
  public UpdatesBodyPerTopic getUpdatesHandlerForPoll(String topic) {
    return new UpdatesBodyPerTopicJSON(topic);
  }

  @Override
  public UpdatesDispatcher getDispatcher(String configuredTarget) {
    UpdatesDispatcherHttp http = new UpdatesDispatcherHttp(configuredTarget);
    return http;
  }

}
