package se.yolean.kafka.keyvalue.onupdate.hc;

import org.apache.http.HttpResponse;
import org.apache.http.client.ServiceUnavailableRetryStrategy;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryServiceUnavailableBackoff implements ServiceUnavailableRetryStrategy {

  static final Logger logger = LoggerFactory.getLogger(RetryServiceUnavailableBackoff.class);

  int previousExecutionCount = -1;

  final RetryDecisions decisions;

  public RetryServiceUnavailableBackoff(RetryDecisions retryDecisions) {
    this.decisions = retryDecisions;
  }

  @Override
  public boolean retryRequest(HttpResponse response, int executionCount, HttpContext context) {
    final int status = response.getStatusLine().getStatusCode();
    final boolean retry = decisions.onStatus(executionCount, status);
    if (!retry) {
      logger.info("Retry={} at count {} for status {}",
          retry, executionCount, status);
      return retry;
    }
    previousExecutionCount = executionCount;
    logger.info("Retry={} with wait {} ms at count {} for status {}",
        retry, getRetryInterval(executionCount), executionCount, status);
    return retry;
  }

  @Override
  public long getRetryInterval() {
    if (previousExecutionCount < 0) {
      throw new IllegalStateException("Backoff calculations assume that getRetryInterval is called immediately after retryRequest");
    }
    long waitMillis = getRetryInterval(previousExecutionCount);
    previousExecutionCount = -1;
    return waitMillis;
  }

  public long getRetryInterval(int executionCount) {
    return (long) (125 * Math.pow(2, executionCount));
  }

}
