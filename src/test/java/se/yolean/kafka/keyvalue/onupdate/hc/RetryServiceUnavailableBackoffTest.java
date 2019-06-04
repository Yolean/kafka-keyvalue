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
