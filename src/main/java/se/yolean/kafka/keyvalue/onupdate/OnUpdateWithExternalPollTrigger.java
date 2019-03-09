package se.yolean.kafka.keyvalue.onupdate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.prometheus.client.Counter;
import se.yolean.kafka.keyvalue.KeyvalueUpdateProcessor;
import se.yolean.kafka.keyvalue.OnUpdate;
import se.yolean.kafka.keyvalue.UpdateRecord;

/**
 * Starts asynchronous requests and polls for completion on external call to
 * {@link #checkCompletion()}.
 *
 * Statically configured, i.e. a fixed set of webhook targets.
 *
 * This implementation assumes that {@link #checkCompletion()} is called from a (one) main control thread,
 * same that would for example poll for readiness, so that no function calls are concurrent.
 */
public class OnUpdateWithExternalPollTrigger implements OnUpdate {

  private static final Logger logger = LogManager.getLogger(OnUpdateWithExternalPollTrigger.class);

  public static final ResponseSuccessCriteria DEFAULT_RESPONSE_SUCCESS_CRITERIA =
      new ResponseSuccessCriteriaStatus200or204();

  /**
   * Was meant to complement kkv_onupdate_completed in {@link KeyvalueUpdateProcessor}
   * but maybe we don't need it now after {@link #onupdateResults} was introduced.
   */
  static final Counter onupdateRequestErrors = Counter.build()
      .name("kkv_onupdate_request_errors").help("Onupdate failures, counts every attempt during retries")
      .labelNames("type", "categorization").register();

  /**
   * The target label here is potentially very long strings, but let's see how that works.
   * Could probably be shortened while still being recognizable.
   */
  static final Counter onupdateResults = Counter.build()
      .name("kkv_onupdate_results")
      .labelNames("target", "status", "error")
      .help("All responses recorded by http status OR java exception, target URL")
      .register();


  private List<Target> targets = new LinkedList<>();

  // LinkedHashMap used to preserve iteration order
  private Map<UpdateRecord, TargetsInvocations> pending = new LinkedHashMap<>(1);

  public OnUpdateWithExternalPollTrigger(
      List<String> onupdateUrls,
      int requestTimeoutMilliseconds,
      int retries) {
    if (onupdateUrls.isEmpty()) {
      logger.warn("Initialization without onupdate urls is only meant for testing");
    }
    for (String url : onupdateUrls) {
      addTarget(url, requestTimeoutMilliseconds, retries);
    }
    logger.info("Initialized onupdate for {} target URLs {}", onupdateUrls.size(), onupdateUrls);
  }

  /**
   * Dynamic reconfiguration is outside scope so this method is kept private
   */
  private void addTarget(String onupdateUrl, int timeoutMs, int retries) {
    HttpTargetRequestInvokerJersey invoker = new HttpTargetRequestInvokerJersey(onupdateUrl, timeoutMs, timeoutMs);
    addTarget(invoker, DEFAULT_RESPONSE_SUCCESS_CRITERIA, retries);
  }

  /**
   * Package-visible so we can mock this in tests.
   * @param targetInvoker based on args to {@link #addTarget(String, int, long)}.
   * @param criteria decides if the response should lead to
   *   {@link Completion#onSuccess()} or retry/{@link Completion#onFailure()}.
   * @param retries the number of retries to use for this target TODO a RetryCriteria interface given the attempt# and request.
   */
  OnUpdateWithExternalPollTrigger addTarget(HttpTargetRequestInvoker targetInvoker,
      ResponseSuccessCriteria criteria, int retries) {
    if (retries != 0) throw new IllegalArgumentException("Support for retries isn't implemented yet");
    targets.add(new Target(targetInvoker, criteria));

    return this;
  }

  @Override
  public void handle(UpdateRecord update, Completion completion) {
    pending.put(update, new TargetsInvocations(update, completion));
  }

  /**
   * @throws UnrecognizedOnupdateResult if any onupdate resulted in neither a response nor a recognized error
   */
  public void checkCompletion() throws UnrecognizedOnupdateResult {
    Iterator<Entry<UpdateRecord, TargetsInvocations>> allPendingUpdates = pending.entrySet().iterator();
    while (allPendingUpdates.hasNext()) {
      Entry<UpdateRecord, TargetsInvocations> pendingUpdate = allPendingUpdates.next();
      UpdateRecord update = pendingUpdate.getKey();
      TargetsInvocations targets = pendingUpdate.getValue();
      if (checkCompletion(update, targets)) {
        allPendingUpdates.remove();
        logger.debug("No pending updates remain for {}", update);
      }
    }
  }

  /**
   * @return true if all targets have completed for this update
   * @throws UnrecognizedOnupdateResult if any onupdate resulted in neither a response nor a recognized error
   */
  boolean checkCompletion(UpdateRecord update, TargetsInvocations targets) throws UnrecognizedOnupdateResult {
    List<TargetInvocation> unfinishedRequests = targets.invocations;
    if (unfinishedRequests.isEmpty()) throw new IllegalStateException("Pending status should have been removed if there are no pending requests: " + update);
    Iterator<TargetInvocation> allRemainingInvocations = unfinishedRequests.iterator();
    while (allRemainingInvocations.hasNext()) {
      TargetInvocation invocation = allRemainingInvocations.next();
      if (invocation.request.isDone()) {
        allRemainingInvocations.remove();
        Response response = null;
        Throwable error = null;
        try {
          response = invocation.request.get();
          onupdateResults.labels(invocation.invoker.toString(), Integer.toString(response.getStatus()), "").inc();
        } catch (InterruptedException e) {
          throw new IllegalStateException("Got interrupted in an operation that should have been synchronous after isDone returned true", e);
        } catch (ExecutionException e) {
          if (e.getCause() == null) {
            throw new IllegalStateException("Failed to get response after isDone returned true, no cause given", e);
          }
          error = e.getCause();
          onupdateResults.labels(invocation.invoker.toString(), "", e.getClass().getSimpleName()).inc();
        }
        boolean result = false;
        if (error == null) {
          result = invocation.criteria.isSuccess(response);
          if (!result) logger.info("Update request failure for {}: {}", invocation, response);
        } else if (error instanceof javax.ws.rs.ProcessingException) {
          Throwable httpError = error.getCause();
          if (httpError instanceof java.net.ConnectException) {
            // target server not responding ("Connection refused") is considered normal, we should retry etc
            logger.info("ConnectException for {}: {}", invocation, error.getMessage());
            onupdateRequestErrors.labels("http", "connection").inc();
          } else if (httpError instanceof java.net.UnknownHostException) {
            logger.warn("Onupdate hostname lookup failed: " + httpError.getMessage());
            onupdateRequestErrors.labels("http", "hostname").inc();
          } else {
            logger.warn("Unrecognized HTTP error for {}: {}", invocation, error.getMessage());
            onupdateRequestErrors.labels("http", "connection").inc();
          }
        } else {
          // TODO Currently this means that the onupdate will never be marked as completed, I think
          logger.error("Failed to recognize error {} from {}", error, invocation.request);
          onupdateRequestErrors.labels("http", "unknown").inc();
          throw new UnrecognizedOnupdateResult(error, invocation.invoker);
        }
        targets.addResult(result);
      }
    }
    if (unfinishedRequests.isEmpty()) {
      if (targets.hasNoFailures()) {
        targets.completion.onSuccess();
      } else {
        targets.completion.onFailure();
      }
      return true;
    }
    return false;
  }

  private class Target {

    private HttpTargetRequestInvoker invoker;
    private ResponseSuccessCriteria criteria;

    public Target(HttpTargetRequestInvoker targetInvoker, ResponseSuccessCriteria criteria) {
      this.invoker = targetInvoker;
      this.criteria = criteria;
    }

  }

  private class TargetsInvocations {

    private Completion completion;
    private List<TargetInvocation> invocations;
    private int successes = 0;
    private int failures = 0;

    TargetsInvocations(UpdateRecord update, Completion completion) {
      this.completion = completion;
      this.invocations = new ArrayList<>(targets.size());
      for (Target target : targets) {
        invocations.add(new TargetInvocation(update, target.invoker, target.criteria));
      }
    }

    private void addResult(boolean success) {
      if (success) {
        successes++;
      } else {
        failures++;
      }
    }

    private boolean hasNoFailures() {
      if (successes + failures == 0) throw new IllegalStateException("Sanity check failed, aggregate result checked before any result has been added");
      return failures == 0;
    }

  }

  private class TargetInvocation {

    private HttpTargetRequestInvoker invoker;
    private ResponseSuccessCriteria criteria;
    private Future<Response> request;
    private final String string;

    TargetInvocation(UpdateRecord update, HttpTargetRequestInvoker invoker, ResponseSuccessCriteria criteria) {
      this.invoker = invoker;
      this.criteria = criteria;
      invoke(update);
      this.string = update.toString() + ',' + invoker;
    }

    private void invoke(UpdateRecord update) {
      request = invoker.postUpdate(update);
    }

    @Override
    public String toString() {
      return string;
    }

  }

}
