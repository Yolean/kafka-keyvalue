package se.yolean.kafka.keyvalue.onupdate;

public interface UpdatesDispatcher {

  void dispatch(String topicName, UpdatesBodyPerTopic body) throws TargetAckFailedException;

  void close();

}
