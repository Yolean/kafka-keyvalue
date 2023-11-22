package se.yolean.kafka.keyvalue;

import java.time.Duration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "kkv")
public interface KafkaCacheConfig {

  @WithDefault("90s")
  Duration getAssignmentsTimeout();

}
