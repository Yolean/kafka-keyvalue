package se.yolean.kafka.keyvalue;

import java.util.Properties;

import se.yolean.kafka.keyvalue.cli.ArgsToOptions;

/**
 * See CLI help in {@link ArgsToOptions} for documentation.
 */
public interface CacheServiceOptions {

  String getApplicationId();

  String getTopicName();

  Integer getPort();

  Properties getStreamsProperties();

  OnUpdate getOnUpdate();

  Integer getStartTimeoutSecods();

  boolean getStandalone();

}
