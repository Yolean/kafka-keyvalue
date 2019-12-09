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

import static org.junit.jupiter.api.Assertions.*;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RetryServiceUnavailableBackoffTest {

  @Test
  void testBackofBasedOnSharedStateAssumingSingleThread() {
    RetryDecisions decisions = Mockito.mock(RetryDecisions.class);
    RetryServiceUnavailableBackoff retry = new RetryServiceUnavailableBackoff(decisions);
    HttpResponse response = Mockito.mock(HttpResponse.class);
    StatusLine statusLine = Mockito.mock(StatusLine.class);
    Mockito.when(response.getStatusLine()).thenReturn(statusLine);
    Mockito.when(statusLine.getStatusCode()).thenReturn(204);
    Mockito.when(decisions.onStatus(1, 204)).thenReturn(true);
    Mockito.when(decisions.onStatus(2, 204)).thenReturn(true);
    Mockito.when(decisions.onStatus(3, 204)).thenReturn(true);
    retry.retryRequest(response, 1, null);
    assertEquals(250, retry.getRetryInterval());
    retry.retryRequest(response, 2, null);
    assertEquals(500, retry.getRetryInterval());
    retry.retryRequest(response, 3, null);
    assertEquals(1000, retry.getRetryInterval());
  }

}
