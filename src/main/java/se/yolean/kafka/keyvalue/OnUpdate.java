package se.yolean.kafka.keyvalue;

public interface OnUpdate {

  void pollStart(Iterable<String> topics);

  /**
   * @param update The new value (which may be the old value at a new offset)
   */
  void handle(UpdateRecord update);

  /**
   * For consistency the only update result that counts is that
   * all targets have acknowledged all updates.
   *
   * Retries are thus implied, and targets may deduplicate requests on topic+partition+offset.
   *
   * @throws RuntimeException on failure to get ack from onupdate targets,
   *   _suppressing_ consumer commit and triggering application exit/restart
   *   TODO or given that we run consumer in a thread, would we rather return a boolean false?
   */
  void pollEndBlockingUntilTargetsAck();

}
