package se.yolean.kafka.keyvalue.onupdate;

import javax.ws.rs.core.Response;

public class ResponseSuccessCriteriaDefaultImpl implements ResponseSuccessCriteria {

  @Override
  public boolean isSuccess(Response response) {
    return response.getStatus() >= 200 && response.getStatus() < 300;
  }

}
