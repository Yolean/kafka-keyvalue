package se.yolean.kafka.keyvalue.onupdate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.ws.rs.core.Response;

import se.yolean.kafka.keyvalue.OnUpdate.Completion;
import se.yolean.kafka.keyvalue.UpdateRecord;

public class RequestWatcherDefaultImpl implements RequestWatcher {

  private Map<UpdateRecord, ResponseWait> watching = new LinkedHashMap<>(1);

  private ExecutorService executor;

  public RequestWatcherDefaultImpl() {
    this(Executors.newSingleThreadExecutor());
  }

  public RequestWatcherDefaultImpl(ExecutorService isDoneExecutor) {
    this.executor = isDoneExecutor;
  }

  @Override
  public Future<Response> watch(UpdateRecord update, Future<Response> res, Completion completion) {
    // TODO Auto-generated method stub
    return null;
  }

  private static class ResponseWait {

    ResponseWait() {

    }

    void add(Future<Response> res, Completion completion) {

    }

  }

}
