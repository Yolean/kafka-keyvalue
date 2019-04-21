package se.yolean.kafka.keyvalue;

import java.util.Properties;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Provider;

@ApplicationScoped
public class ConfigureKafkaClient implements Provider<Properties> {

  @Produces
  @javax.inject.Named("consumer")
  @Override
  public Properties get() {
    Properties props = new Properties();
    return props;
  }

}
