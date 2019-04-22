package se.yolean.kafka.keyvalue.onupdate;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.yolean.kafka.keyvalue.OnUpdate;
import se.yolean.kafka.keyvalue.UpdateRecord;

/**
 * Maintains our update semantics and reports status back for commit decisions.
 */
public class OnUpdatePerPoll implements OnUpdate {

  static final Logger logger = LoggerFactory.getLogger(OnUpdatePerPoll.class);

  private List<TargetInvocations> targets;

  public OnUpdatePerPoll(List<TargetInvocations> targets) {
    if (targets.size() == 0) throw new IllegalArgumentException("Use NOP onupdate when there's zero targets");
    this.targets = targets;
  }

  @Override
  public void pollStart(Iterable<String> topics) {
    logger.warn("Not implemented: pollStart");
  }

  @Override
  public void handle(UpdateRecord update) {
    logger.warn("Not implemented: handle");
  }

  @Override
  public void pollEndBlockingUntilTargetsAck() {
    logger.warn("Not implemented: pollEnd. Targets: {}", targets);
  }

}
