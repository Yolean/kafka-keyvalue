package se.yolean.kafka.keyvalue.onupdate;

import javax.ws.rs.core.Response;

public interface ResponseSuccessCriteria {

  /**
   * @param response A completed response (i.e. either success or failure)
   * @return true if response is a success, false if failed
   */
  boolean isSuccess(Response response);

}
