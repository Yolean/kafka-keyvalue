package se.yolean.kafka.keyvalue.healthz;

import org.apache.kafka.streams.KafkaStreams.State;
import org.apache.kafka.streams.KafkaStreams.StateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Needed because transitions say more than calls to streams.state()
 */
public class StreamsStateListener implements StateListener {

  public final Logger logger = LoggerFactory.getLogger(StreamsStateListener.class);

  private boolean hasBeenRunning = false;

  /**
   * @return true if streams seems to have been running at any time
   */
  public boolean streamsHasBeenRunning() {
    return hasBeenRunning;
  }

  @Override
  public void onChange(State newState, State oldState) {
    logger.info("Streams state change to {} from {}", newState, oldState);
    if (State.RUNNING.equals(newState) && State.REBALANCING.equals(oldState)) {
      hasBeenRunning = true;
    }
  }

}
