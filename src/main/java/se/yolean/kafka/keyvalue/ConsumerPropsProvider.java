package se.yolean.kafka.keyvalue;

import java.util.Properties;

import javax.inject.Provider;

public class ConsumerPropsProvider implements Provider<Properties> {

  @Override
  public Properties get() {
    Properties props = new Properties();
    return props;
  }

}
