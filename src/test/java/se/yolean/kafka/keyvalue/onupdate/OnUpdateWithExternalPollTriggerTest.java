package se.yolean.kafka.keyvalue.onupdate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;

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

  @Test
  void testSingleSuccessful() throws InterruptedException, ExecutionException {
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
  void testSingleFailure() throws InterruptedException, ExecutionException {
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
  void testMultipleAllSuccessful() throws InterruptedException, ExecutionException {
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
  void testMultipleOneFailed() throws InterruptedException, ExecutionException {
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
