package se.yolean.kafka.keyvalue.kubernetes;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "kkv.target.service")
public interface EndpointsWatcherConfig {

  @WithName("name")
  public Optional<String> targetServiceName();

}
