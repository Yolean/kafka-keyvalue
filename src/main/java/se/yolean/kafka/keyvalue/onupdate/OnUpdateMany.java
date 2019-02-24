package se.yolean.kafka.keyvalue.onupdate;

import java.util.List;

import se.yolean.kafka.keyvalue.OnUpdate;
import se.yolean.kafka.keyvalue.UpdateRecord;

public class OnUpdateMany implements OnUpdate {

  private List<OnUpdate> all;

  /**
   * @see OnUpdateFactory
   */
  OnUpdateMany(List<OnUpdate> many) {
    this.all = many;
  }

  @Override
  public void handle(UpdateRecord update, Runnable onSuccess) {
    all.forEach(single -> {
      if (single instanceof OnUpdateHttpIgnoreResult) {
        single.handle(update, null);
      } else {
        throw new IllegalArgumentException("No support yet for 1+ onupdate of type " + update.getClass().getName());
      }
    });
  }

}
