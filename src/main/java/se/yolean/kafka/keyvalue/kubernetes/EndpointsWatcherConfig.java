package se.yolean.kafka.keyvalue.kubernetes;

import java.time.Duration;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "kkv")
public interface EndpointsWatcherConfig {

  @WithName("target.service.name")
  public Optional<String> targetServiceName();

  @WithName("endpoints-watcher.watch-restart-delay-min")
  public Duration watchRestartDelayMin();

  @WithName("endpoints-watcher.watch-restart-delay-max")
  public Duration watchRestartDelayMax();

}
