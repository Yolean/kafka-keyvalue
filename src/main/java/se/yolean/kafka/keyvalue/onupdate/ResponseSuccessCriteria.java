package se.yolean.kafka.keyvalue.onupdate;

import javax.ws.rs.core.Response;

public interface ResponseSuccessCriteria {

  boolean isSuccess(Response response);

}
