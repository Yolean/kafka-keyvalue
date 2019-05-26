package se.yolean.kafka.keyvalue.onupdate.hc;

import java.time.Duration;

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import se.yolean.kafka.keyvalue.onupdate.DispatcherConfig;
import se.yolean.kafka.keyvalue.onupdate.UpdatesBodyPerTopic;
import se.yolean.kafka.keyvalue.onupdate.UpdatesBodyPerTopicJSON;
import se.yolean.kafka.keyvalue.onupdate.UpdatesDispatcher;

@ApplicationScoped
public class DispatcherConfigHttpclient implements DispatcherConfig {

  @ConfigProperty(name="update_connection_timeout", defaultValue="1")
  Duration connectionTimeout;

  @Override
  public UpdatesBodyPerTopic getUpdatesHandlerForPoll(String topic) {
    return new UpdatesBodyPerTopicJSON(topic);
  }

  @Override
  public UpdatesDispatcher getDispatcher(String configuredTarget) {
    return new UpdatesDispatcherHttp(configuredTarget);
  }

}
