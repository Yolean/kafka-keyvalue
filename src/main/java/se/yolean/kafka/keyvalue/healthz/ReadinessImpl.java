package se.yolean.kafka.keyvalue.healthz;

import se.yolean.kafka.keyvalue.KafkaCache;
import se.yolean.kafka.keyvalue.Readiness;
import se.yolean.kafka.keyvalue.metrics.StreamsMetrics;

public class ReadinessImpl implements Readiness {

  private StreamsStateListener state;
  private StreamsMetrics metrics;
  private KafkaCache cache;

  private Runnable httpEnable = null;
  private Runnable httpDisable = null;

  public ReadinessImpl(
      KafkaCache cache,
      StreamsStateListener state,
      StreamsMetrics metrics) {
    this.state = state;
    this.metrics = metrics;
    this.cache = cache;
  }

  public ReadinessImpl setHttpEnable(Runnable httpEnable) {
    this.httpEnable = httpEnable;
    return this;
  }

  public ReadinessImpl setHttpDisable(Runnable httpDisable) {
    this.httpDisable = httpDisable;
    return this;
  }

  @Override
  public boolean isAppReady() {
    metrics.check();
    return
        // processor
        cache.isReady() &&
        // deals with startup states, for now we're ok if streams has ever started (we don't go unready again based on streams state)
        state.streamsHasBeenRunning() &&
        // better information about streams can be found in metrics
        metrics.hasSeenAssignedParititions();
  }

  @Override
  public void enableServiceEndpoints() {
    if (this.httpEnable == null) throw new UnsupportedOperationException("Missing httpEnable hook");
    this.httpEnable.run();
  }

  @Override
  public void disableServiceEndpoints() {
    if (this.httpDisable == null) throw new UnsupportedOperationException("Missing httpDisable hook");
    this.httpDisable.run();
  }

}
