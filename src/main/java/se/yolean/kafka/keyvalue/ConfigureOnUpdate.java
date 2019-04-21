package se.yolean.kafka.keyvalue;

import javax.inject.Provider;

public class LoggingOnUpdateProvider implements Provider<OnUpdate> {

  @Override
  public OnUpdate get() {
    throw new UnsupportedOperationException("Not implemented");
  }

}
