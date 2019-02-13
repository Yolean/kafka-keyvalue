package se.yolean.kafka.keyvalue;

public interface OnUpdate {

  /**
   *
   * Transitional strategy for handling downstream errors:
   * - Keep retrying
   * + don't return success from any subsequent {@link #handle(String, Runnable)}
   * + when bailing throw on the next handle
   * = should lead to service restart without commits from the failed offset.
   *
   * @param key The key that got a new value (currently includes a new message with same value)
   * @param onSuccess If the hook succeeds. TBD what to do if a number of hooks fail or hang.
   */
  void handle(String key, Runnable onSuccess);

}
