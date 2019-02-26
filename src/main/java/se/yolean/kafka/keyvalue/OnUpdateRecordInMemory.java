package se.yolean.kafka.keyvalue;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class OnUpdateRecordInMemory implements OnUpdate {

  private LinkedList<UpdateRecord> updates = new LinkedList<UpdateRecord>();

  public OnUpdateRecordInMemory() {
  }

  @Override
  public void handle(UpdateRecord update, Completion completion) {
    updates.add(update);
  }

  public List<UpdateRecord> getAll() {
    return Collections.unmodifiableList(updates);
  }

}
