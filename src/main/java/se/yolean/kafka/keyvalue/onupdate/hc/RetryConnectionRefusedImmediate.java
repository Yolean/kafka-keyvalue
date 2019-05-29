package se.yolean.kafka.keyvalue.onupdate.hc;

import java.io.IOException;

import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryConnectionRefusedImmediate implements HttpRequestRetryHandler {

  static final Logger logger = LoggerFactory.getLogger(RetryConnectionRefusedImmediate.class);

  final RetryDecisions decisions;

  public RetryConnectionRefusedImmediate(RetryDecisions descisions) {
    this.decisions = descisions;
  }

  @Override
  public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
    boolean retry = decisions.onConnectionRefused(executionCount);
    logger.info("Retry={} at count {} for what we assume is connection refused: {}",
        retry, executionCount, exception.toString());
    return retry;
  }

}
