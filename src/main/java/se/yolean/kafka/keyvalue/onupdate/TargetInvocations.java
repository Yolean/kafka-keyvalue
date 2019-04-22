package se.yolean.kafka.keyvalue.onupdate;

import java.util.Map;
import java.util.concurrent.Future;

/**
 * Maintains now to trigger per-topic update messages to a target.
 */
public interface TargetInvocations {

  /**
   * @param batches topic-to-body mapping, length at least 1
   * @return true when <em>all</em> updates succeeded, false if any failed (the rest succeeded or aborted)
   */
  Future<Boolean> trigger(Iterable<Map.Entry<String,UpdatesBodyPerTopic>> batches);

}
