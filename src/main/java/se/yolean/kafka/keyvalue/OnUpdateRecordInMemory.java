package se.yolean.kafka.keyvalue;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OnUpdateRecordInMemory implements OnUpdate {

  private LinkedList<UpdateRecord> updates = new LinkedList<UpdateRecord>();
  private ExecutorService executor;

  public OnUpdateRecordInMemory() {
    this.executor = Executors.newSingleThreadExecutor();
  }

  @Override
  public void handle(UpdateRecord update, Runnable onSuccess, Runnable onFailure) {
    updates.add(update);
    executor.submit(onSuccess);
  }

  public List<UpdateRecord> getAll() {
    return Collections.unmodifiableList(updates);
  }

}
