package se.yolean.kafka.keyvalue.onupdate;

import java.util.concurrent.Future;

import javax.ws.rs.core.Response;

import se.yolean.kafka.keyvalue.OnUpdate.Completion;
import se.yolean.kafka.keyvalue.UpdateRecord;

/**
 * Is responsible for calling {@link Completion#onFailure()} or {@link Completion#onSuccess()} when appropriate.
 */
public interface RequestWatcher {

  Future<Response> watch(UpdateRecord update, Future<Response> res, Completion completion);

}
