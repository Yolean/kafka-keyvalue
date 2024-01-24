package se.yolean.kafka.keyvalue.kubernetes;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "kkv")
public interface EndpointsWatcherConfig {

  @WithName("target.service.name")
  public Optional<String> targetServiceName();

  @WithName("endpoints-watcher.watch-restart-delay-seconds")
  public Integer watchRestartDelaySeconds();

}
