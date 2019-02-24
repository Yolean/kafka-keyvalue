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
   * @param key The new value (which may be the old value at a new offset)
   * @param onSuccess If the hooks succeed (after retries, if applicable)
   * @param onFailure If any of the hooks fail (after retries, if applicable)
   */
  void handle(UpdateRecord update, Runnable onSuccess, Runnable onFailure);

}
