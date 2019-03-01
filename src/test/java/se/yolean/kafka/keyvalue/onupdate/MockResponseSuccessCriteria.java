package se.yolean.kafka.keyvalue.onupdate;

import javax.ws.rs.core.Response;

public class MockResponseSuccessCriteria implements ResponseSuccessCriteria {

  @Override
  public boolean isSuccess(Response response) {
    return response.getStatus() == 200;
  }

}
