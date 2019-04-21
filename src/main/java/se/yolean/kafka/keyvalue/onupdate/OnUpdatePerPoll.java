package se.yolean.kafka.keyvalue.onupdate;

import javax.enterprise.context.ApplicationScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.yolean.kafka.keyvalue.OnUpdate;
import se.yolean.kafka.keyvalue.UpdateRecord;

@ApplicationScoped
public class OnUpdatePerPoll implements OnUpdate {

  static final Logger logger = LoggerFactory.getLogger(OnUpdatePerPoll.class);

  @Override
  public void pollStart() {
    logger.warn("Not implemented: pollStart");
  }

  @Override
  public void handle(UpdateRecord update) {
    logger.warn("Not implemented: handle");
  }

  @Override
  public void pollEndBlockingUntilTargetsAck() {
    logger.warn("Not implemented: pollEnd");
  }

}
