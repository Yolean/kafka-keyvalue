package se.yolean.kafka.keyvalue.kubernetes;

import java.time.Duration;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "kkv")
public interface EndpointsWatcherConfig {

  @WithName("namespace")
  public String namespace();

  @WithName("target.service.name")
  public Optional<String> targetServiceName();

  /** @return How often the informer rebuilds it cache. */
  @WithName("endpoints-watcher.resync-period")
  public Duration resyncPeriod();

}
