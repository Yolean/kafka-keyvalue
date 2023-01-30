package se.yolean.kafka.keyvalue.onupdate.webclient;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "kkv")
public interface UpdatesDispatcherWebclientConfig {

  @WithName("target.service.port")
  int targetServicePort();

  @WithName("target.path")
  String targetPath();

  @WithName("dispatcher.timeout-seconds")
  long timeoutSeconds();

  @WithName("dispatcher.backoff-seconds")
  long backoffSeconds();

}
