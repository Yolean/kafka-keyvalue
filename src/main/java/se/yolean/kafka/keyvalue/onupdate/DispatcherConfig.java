package se.yolean.kafka.keyvalue.onupdate;

public interface DispatcherConfig {

  UpdatesBodyPerTopic getUpdatesHandlerForPoll(String topic);

  UpdatesDispatcher getDispatcher(String configuredTarget);

}
