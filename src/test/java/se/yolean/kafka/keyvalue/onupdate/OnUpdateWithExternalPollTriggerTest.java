package se.yolean.kafka.keyvalue.onupdate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.ws.rs.client.AsyncInvoker;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import se.yolean.kafka.keyvalue.OnUpdate;
import se.yolean.kafka.keyvalue.UpdateRecord;

class OnUpdateWithExternalPollTriggerTest {

  private static final List<String> DUMMY_INIT = Collections.unmodifiableList(new LinkedList<String>());

  @Test
  void testMockRequestSuccess() throws InterruptedException, ExecutionException {
    MockRequest request = new MockRequest();
    MockResponseSuccessCriteria criteria = new MockResponseSuccessCriteria();
    assertFalse(request.isDone());
    request.setSuccess();
    assertTrue(request.isDone());
    assertTrue(criteria.isSuccess(request.get()));
  }

  @Test
  void testMockRequestFailure() throws InterruptedException, ExecutionException {
    MockRequest request = new MockRequest();
    MockResponseSuccessCriteria criteria = new MockResponseSuccessCriteria();
    assertFalse(request.isDone());
    request.setFailure();
    assertTrue(request.isDone());
    assertFalse(criteria.isSuccess(request.get()));
  }

  @Disabled // was used to discover the breaking effect of using long for timeout millis
  @Test
  void testRealRequest() throws InterruptedException, ExecutionException {
    String onupdateTargetUrl = "http://yolean.com";
    int connectTimeoutMilliseconds = 5000;
    int readTimeoutMilliseconds = 5000;
    ClientConfig configuration = new ClientConfig();
    configuration.property(ClientProperties.CONNECT_TIMEOUT, connectTimeoutMilliseconds);
    configuration.property(ClientProperties.READ_TIMEOUT, readTimeoutMilliseconds);
    Client client = ClientBuilder.newClient(configuration);

    WebTarget target = client.target(onupdateTargetUrl);
    AsyncInvoker async = target.request().async();
    UpdateRecord update = new UpdateRecord("t", 0, 1, "k");
    Future<Response> request = async.post(Entity.entity(update, MediaType.APPLICATION_JSON_TYPE));
    assertFalse(request.isDone());
    while (!request.isDone()) {
      Thread.sleep(100);
    }
    Response response = request.get();
    assertNotNull(response);
  }

  @Test
  void testSingleSuccessful() throws InterruptedException, ExecutionException, UnrecognizedOnupdateResult {
    HttpTargetRequestInvoker invoker = mock(HttpTargetRequestInvoker.class);
    OnUpdateWithExternalPollTrigger updates = new OnUpdateWithExternalPollTrigger(
        DUMMY_INIT, -1, -1)
        .addTarget(invoker, new MockResponseSuccessCriteria(), 0);

    OnUpdate.Completion completion = mock(OnUpdate.Completion.class);
    UpdateRecord update = new UpdateRecord("topic1", 0, 15, "key1");

    MockRequest request = new MockRequest();
    when(invoker.postUpdate(update)).thenReturn(request);
    updates.handle(update, completion);

    updates.checkCompletion();
    verify(completion, never()).onSuccess();

    request.setSuccess();
    updates.checkCompletion();
    verify(completion).onSuccess();
    verify(completion, never()).onFailure();
  }

  @Test
  void testSingleFailure() throws InterruptedException, ExecutionException, UnrecognizedOnupdateResult {
    HttpTargetRequestInvoker invoker = mock(HttpTargetRequestInvoker.class);
    OnUpdateWithExternalPollTrigger updates = new OnUpdateWithExternalPollTrigger(
        DUMMY_INIT, -1, -1)
        .addTarget(invoker, new MockResponseSuccessCriteria(), 0);

    OnUpdate.Completion completion = mock(OnUpdate.Completion.class);
    UpdateRecord update = new UpdateRecord("topic1", 0, 15, "key1");

    MockRequest request = new MockRequest();
    when(invoker.postUpdate(update)).thenReturn(request);
    updates.handle(update, completion);

    updates.checkCompletion();
    verify(completion, never()).onSuccess();

    request.setFailure();
    updates.checkCompletion();
    verify(completion, never()).onSuccess();
    verify(completion).onFailure();
  }

  @Test
  void testMultipleAllSuccessful() throws InterruptedException, ExecutionException, UnrecognizedOnupdateResult {
    HttpTargetRequestInvoker invoker1 = mock(HttpTargetRequestInvoker.class);
    HttpTargetRequestInvoker invoker2 = mock(HttpTargetRequestInvoker.class);
    OnUpdateWithExternalPollTrigger updates = new OnUpdateWithExternalPollTrigger(
        DUMMY_INIT, -1, -1)
        .addTarget(invoker1, new MockResponseSuccessCriteria(), 0)
        .addTarget(invoker2, new MockResponseSuccessCriteria(), 0);

    OnUpdate.Completion completion = mock(OnUpdate.Completion.class);
    UpdateRecord update = new UpdateRecord("topic1", 0, 15, "key1");

    MockRequest request1 = new MockRequest();
    when(invoker1.postUpdate(update)).thenReturn(request1);
    MockRequest request2 = new MockRequest();
    when(invoker2.postUpdate(update)).thenReturn(request2);

    updates.handle(update, completion);

    updates.checkCompletion();
    verify(completion, never()).onSuccess();
    verify(completion, never()).onFailure();

    // only one completed
    request1.setSuccess();
    updates.checkCompletion();
    verify(completion, never()).onSuccess();
    verify(completion, never()).onFailure();

    request2.setSuccess();
    updates.checkCompletion();
    verify(completion).onSuccess();
    verify(completion, never()).onFailure();
  }

  @Test
  void testMultipleOneFailed() throws InterruptedException, ExecutionException, UnrecognizedOnupdateResult {
    HttpTargetRequestInvoker invoker1 = mock(HttpTargetRequestInvoker.class);
    HttpTargetRequestInvoker invoker2 = mock(HttpTargetRequestInvoker.class);
    OnUpdateWithExternalPollTrigger updates = new OnUpdateWithExternalPollTrigger(
        DUMMY_INIT, -1, -1)
        .addTarget(invoker1, new MockResponseSuccessCriteria(), 0)
        .addTarget(invoker2, new MockResponseSuccessCriteria(), 0);

    OnUpdate.Completion completion = mock(OnUpdate.Completion.class);
    UpdateRecord update = new UpdateRecord("topic1", 0, 15, "key1");

    MockRequest request1 = new MockRequest();
    when(invoker1.postUpdate(update)).thenReturn(request1);
    MockRequest request2 = new MockRequest();
    when(invoker2.postUpdate(update)).thenReturn(request2);

    updates.handle(update, completion);

    updates.checkCompletion();
    verify(completion, never()).onSuccess();
    verify(completion, never()).onFailure();

    // only one completed
    request1.setFailure();
    request2.setSuccess();
    updates.checkCompletion();
    verify(completion, never()).onSuccess();
    verify(completion).onFailure();
  }

}
