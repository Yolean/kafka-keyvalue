package se.yolean.kafka.keyvalue.onupdate.hc;

import org.apache.http.HttpResponse;
import org.apache.http.client.ServiceUnavailableRetryStrategy;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryServiceUnavailableBackoff implements ServiceUnavailableRetryStrategy {

  static final Logger logger = LoggerFactory.getLogger(RetryServiceUnavailableBackoff.class);

  int waitPeriod = 125;

  final RetryDecisions decisions;

  public RetryServiceUnavailableBackoff(RetryDecisions retryDecisions) {
    this.decisions = retryDecisions;
  }

  @Override
  public boolean retryRequest(HttpResponse response, int executionCount, HttpContext context) {
    final int status = response.getStatusLine().getStatusCode();
    final boolean retry = decisions.onStatus(executionCount, status);
    waitPeriod *= 2;
    logger.info("Retry={} with wait {}ms at count {} for status {}", retry, waitPeriod, executionCount, status);
    return retry;
  }

  @Override
  public long getRetryInterval() {
    return waitPeriod;
  }

}
