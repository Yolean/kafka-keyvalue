package se.yolean.kafka.keyvalue.kubernetes;

import java.time.Duration;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "kkv.target.service")
public interface EndpointsWatcherConfig {

  @WithName("namespace")
  public Optional<String> targetServiceNamespace();

  @WithName("name")
  public Optional<String> targetServiceName();

  /** @return How often the informer rebuilds it cache. */
  @WithName("informer-resync-period")
  public Duration informerResyncPeriod();

}
