package se.yolean.kafka.keyvalue.onupdate.hc;

import java.io.IOException;

import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryConnectionRefusedThreadSleep implements HttpRequestRetryHandler {

  static final Logger logger = LoggerFactory.getLogger(RetryConnectionRefusedThreadSleep.class);

  final RetryDecisions decisions;

  public RetryConnectionRefusedThreadSleep(RetryDecisions descisions) {
    this.decisions = descisions;
  }

  @Override
  public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
    final boolean retry = decisions.onConnectionRefused(executionCount);
    if (!retry) {
      logger.info("Retry={} at count {} for what we assume is connection refused: {}",
          retry, executionCount, exception.toString());
      return retry;
    }
    long waitMillis = getRetryInterval(executionCount);
    logger.info("Retry={} with wait {} ms at count {} for what we assume is connection refused: {}",
        retry, waitMillis, executionCount, exception.toString());
    forceSleep(waitMillis);
    return retry;
  }

  public long getRetryInterval(int executionCount) {
    return (long) (125 * Math.pow(2, executionCount));
  }

  void forceSleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      logger.warn("Failed to delay retry", e);
    }
  }

}
