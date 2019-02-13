package se.yolean.kafka.keyvalue;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.Test;

class OnUpdateRecordInMemoryTest {

  @Test
  void testHandleIsAsync() throws InterruptedException {
    OnUpdateRecordInMemory onUpdate = new OnUpdateRecordInMemory();
    assertEquals(onUpdate.getAll().size(), 0);
    final List<Object> ok = new LinkedList<Object>();
    onUpdate.handle(new UpdateRecord("test", 0, 0, "0".getBytes()), new Runnable() {
      @Override
      public void run() {
        ok.add(null);
      }
    });
    assertEquals(1, onUpdate.getAll().size());
    assertEquals(0, ok.size());
    Thread.sleep(1);
    assertEquals(1, ok.size());
  }

}
