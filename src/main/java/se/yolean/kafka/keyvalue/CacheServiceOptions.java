package se.yolean.kafka.keyvalue;

import java.util.Properties;

public interface CacheServiceOptions {

  String getApplicationId();

  String getTopicName();

  Integer getPort();

  Properties getStreamsProperties();

  OnUpdate getOnUpdate();

}