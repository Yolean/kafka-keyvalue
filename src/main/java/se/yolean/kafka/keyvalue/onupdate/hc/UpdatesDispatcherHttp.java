package se.yolean.kafka.keyvalue.onupdate.hc;

import se.yolean.kafka.keyvalue.onupdate.TargetAckFailedException;
import se.yolean.kafka.keyvalue.onupdate.UpdatesDispatcher;
import se.yolean.kafka.keyvalue.onupdate.UpdatesBodyPerTopic;

public class UpdatesDispatcherHttp implements UpdatesDispatcher {

  public UpdatesDispatcherHttp(String configuredTarget) {
    // TODO Auto-generated constructor stub
  }

  @Override
  public void dispatch(String topicName, UpdatesBodyPerTopic body) throws TargetAckFailedException {
    // TODO Auto-generated method stub

  }

  @Override
  public void close() {
    // TODO Auto-generated method stub

  }

}
