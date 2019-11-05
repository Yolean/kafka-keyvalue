// Copyright 2019 Yolean AB
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

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
