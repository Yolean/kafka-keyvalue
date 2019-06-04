package se.yolean.kafka.keyvalue.onupdate.hc;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;

public class ResponseHandlerAck implements ResponseHandler<ResponseResult> {

  @Override
  public ResponseResult handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
    StatusLine status = response.getStatusLine();
    ResponseResult result = new ResponseResult(status.getStatusCode());
    return result;
  }

}
