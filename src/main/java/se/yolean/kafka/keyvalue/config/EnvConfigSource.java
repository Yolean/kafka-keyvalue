package se.yolean.kafka.keyvalue.config;

import java.util.Map;

import org.eclipse.microprofile.config.spi.ConfigSource;

public class EnvConfigSource implements ConfigSource {

  public static final String ENV_NAME = "APP_CONFIG";

  public EnvConfigSource() {
    String config = System.getenv(ENV_NAME);
    if (config == null || config.length() == 0) throw new IllegalStateException("Missing configuration env " + ENV_NAME);
    // TODO YAML with |+ in k8s manifests and docker-compose.yml
    // TODO hot reload? https://github.com/quarkusio/quarkus/issues/1772
  }

  @Override
  public Map<String, String> getProperties() {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public String getValue(String propertyName) {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public String getName() {
    throw new UnsupportedOperationException("not implemented");
  }

}
