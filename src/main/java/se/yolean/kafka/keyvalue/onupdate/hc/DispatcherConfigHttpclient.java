package se.yolean.kafka.keyvalue.onupdate.hc;

import javax.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.metrics.annotation.Counted;

import se.yolean.kafka.keyvalue.onupdate.DispatcherConfig;
import se.yolean.kafka.keyvalue.onupdate.UpdatesBodyPerTopic;
import se.yolean.kafka.keyvalue.onupdate.UpdatesBodyPerTopicJSON;
import se.yolean.kafka.keyvalue.onupdate.UpdatesDispatcher;

@Singleton
public class DispatcherConfigHttpclient implements DispatcherConfig, RetryDecisions {

  //@ConfigProperty(name="update_connection_timeout", defaultValue="1")
  //Duration connectionTimeout;

  @ConfigProperty(name="max_retries_connection_refused", defaultValue="8")
  int maxRetriesConnectionRefused;

  @ConfigProperty(name="max_retries_connection_refused", defaultValue="8")
  int maxRetriesStatus;

  @Override
  public UpdatesBodyPerTopic getUpdatesHandlerForPoll(String topic) {
    return new UpdatesBodyPerTopicJSON(topic);
  }

  @Override
  public UpdatesDispatcher getDispatcher(String configuredTarget) {
    UpdatesDispatcherHttp http = new UpdatesDispatcherHttp(
        configuredTarget,
        (RetryDecisions) this);
    return http;
  }

  @Override
  @Counted(name="retries_connection_refused", description="Retries granted for errors that look like connection refused")
  public boolean onConnectionRefused(int count) {
    return count <= maxRetriesConnectionRefused;
  }

  @Override
  @Counted(name="retries_status", description="Retries granted for unexpected http statuses")
  public boolean onStatus(int count, int status) {
    if (ResponseResult.isAck(status)) return false;
    return count <= maxRetriesStatus;
  }

}
