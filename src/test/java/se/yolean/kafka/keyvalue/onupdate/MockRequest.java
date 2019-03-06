package se.yolean.kafka.keyvalue.onupdate;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.core.Response;

import org.mockito.Mockito;

public class MockRequest implements Future<Response> {

  private Response response = Mockito.mock(Response.class);
  private ExecutionException getWillThrow = null;

  /**
   * Coupled to {@link MockResponseSuccessCriteria#isSuccess(Response)}.
   */
  void setSuccess() {
    Mockito.when(response.getStatus()).thenReturn(200);
  }

  /**
   * Coupled to {@link MockResponseSuccessCriteria#isSuccess(Response)} false.
   */
  void setFailure() {
    Mockito.when(response.getStatus()).thenReturn(500);
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public boolean isCancelled() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public boolean isDone() {
    return getWillThrow != null ||
        response.getStatus() == 200 || response.getStatus() == 500;
  }

  @Override
  public Response get() throws InterruptedException, ExecutionException {
    if (getWillThrow != null) throw getWillThrow;
    return response;
  }

  @Override
  public Response get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void setThrow(ExecutionException exception) {
    this.getWillThrow = exception;
  }

}
