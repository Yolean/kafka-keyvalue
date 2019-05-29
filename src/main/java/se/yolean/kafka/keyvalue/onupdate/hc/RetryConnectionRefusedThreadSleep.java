package se.yolean.kafka.keyvalue.onupdate.hc;

import java.io.IOException;

import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryConnectionRefusedThreadSleep implements HttpRequestRetryHandler {

  static final Logger logger = LoggerFactory.getLogger(RetryConnectionRefusedThreadSleep.class);

  int waitPeriod = 125;

  final RetryDecisions decisions;

  public RetryConnectionRefusedThreadSleep(RetryDecisions descisions) {
    this.decisions = descisions;
  }

  @Override
  public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
    boolean retry = decisions.onConnectionRefused(executionCount);
    waitPeriod *= 2;
    logger.info("Retry={} with wait {}ms at count {} for what we assume is connection refused: {}",
        retry, waitPeriod, executionCount, exception.toString());
    forceSleep(waitPeriod);
    return retry;
  }

  public long getRetryInterval() {
    return waitPeriod;
  }

  void forceSleep(int millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      logger.warn("Failed to delay retry", e);
    }
  }

}
