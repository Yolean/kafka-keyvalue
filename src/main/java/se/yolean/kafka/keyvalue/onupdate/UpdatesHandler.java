package se.yolean.kafka.keyvalue.onupdate;

import se.yolean.kafka.keyvalue.UpdateRecord;

public interface UpdatesHandler {

  void handle(UpdateRecord update);

}