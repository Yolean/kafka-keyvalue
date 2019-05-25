package se.yolean.kafka.keyvalue.onupdate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.yolean.kafka.keyvalue.OnUpdate;
import se.yolean.kafka.keyvalue.UpdateRecord;

public class OnUpdateNOP implements OnUpdate {

  static final Logger logger = LoggerFactory.getLogger(OnUpdateNOP.class);

  private String message;

  public OnUpdateNOP(String message) {
    this.message = message;
    logger.info("OnUpdate is NOP: {}", message);
  }

  @Override
  public void pollStart(Iterable<String> topics) {
    logger.debug("OnUpdate is NOP: {}", message);
  }

  @Override
  public void handle(UpdateRecord update) {
    logger.trace("OnUpdate is NOP: {}", message);
  }

  @Override
  public void pollEndBlockingUntilTargetsAck() {
    logger.debug("OnUpdate is NOP, ack is implied");
  }

}
