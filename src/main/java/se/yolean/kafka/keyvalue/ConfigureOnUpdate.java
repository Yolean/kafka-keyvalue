package se.yolean.kafka.keyvalue;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class ConfigureOnUpdate implements Provider<OnUpdate> {

  private int count = 0;

  @Produces
  @Override
  public OnUpdate get() {
    count++;

    return new OnUpdate() {

      final Logger logger = LoggerFactory.getLogger(this.getClass());

      @Override
      public void handle(UpdateRecord update, Completion completion) {
        logger.warn("OnUpdate (#{}) not configured. Record {}. Completion {}.", count, update, completion);
      }

    };
  }

}
