package se.yolean.kafka.keyvalue.onupdate;

import java.util.concurrent.Future;

import javax.ws.rs.core.Response;

import se.yolean.kafka.keyvalue.UpdateRecord;

public interface HttpTargetRequestInvoker {

  Future<Response> postUpdate(UpdateRecord update);

}
