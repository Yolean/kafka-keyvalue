package se.yolean.kafka.keyvalue;

public interface Readiness {

  /**
   * @return True if we can't find any sign of unreadiness in any component
   */
  boolean isAppReady();

  /**
   * Pod/container readiness is probably {@link #isAppReady()} + http started,
   * and the latter should not happen until first app readiness.
   */
  void enableServiceEndpoints();

  /**
   * Stops all responding to HTTP requests
   * including, unfortunately, the /metrics endpoint.
   */
  void disableServiceEndpoints();

}
