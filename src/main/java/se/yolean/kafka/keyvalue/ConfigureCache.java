package se.yolean.kafka.keyvalue;

import java.util.HashMap;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Named;
import javax.inject.Provider;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class CacheInMemoryProvider implements Provider<Map<String, byte[]>> {

  final Logger logger = LoggerFactory.getLogger(this.getClass());

  @ConfigProperty(name="cache_initial_size", defaultValue="0")
  private int initialSize;

  @Produces
  @Named("cache")
  @Override
  public Map<String, byte[]> get() {
    logger.info("Providing new cache, initial size {}", initialSize);
    return new HashMap<String, byte[]>(initialSize);
  }

}
