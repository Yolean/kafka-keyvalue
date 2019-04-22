package se.yolean.kafka.keyvalue.config;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WIP Experimental yaml-in-yaml config, a single env value for {@value #ENV_NAME}.
 */
public class EnvConfigSource implements ConfigSource {

  final Logger logger = LoggerFactory.getLogger(EnvConfigSource.class);

  public static final String ENV_NAME = "APP_CONFIG";

  public EnvConfigSource() {
    String config = System.getenv(ENV_NAME);
    if (config == null) {
      logger.info("Env {} not set", ENV_NAME);
      return;
    }
    if (config.length() == 0) {
      throw new IllegalStateException("Env " + ENV_NAME + " exists but is empty");
    }
    logger.error("Env {} exists but this config source isn't implemented yet", ENV_NAME);
  }

  @Override
  public Map<String, String> getProperties() {
    return new HashMap<>(0);
  }

  @Override
  public String getValue(String propertyName) {
    return null;
  }

  @Override
  public String getName() {
    return "Env config source " + ENV_NAME;
  }

}
