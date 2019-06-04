package se.yolean.kafka.keyvalue.onupdate.hc;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RetryConnectionRefusedThreadSleepTest {

  @Test
  void testGetRetryIntervalMillis() {
    RetryDecisions decisions = Mockito.mock(RetryDecisions.class);
    RetryConnectionRefusedThreadSleep retry = new RetryConnectionRefusedThreadSleep(decisions);
    assertEquals(250, retry.getRetryInterval(1));
    assertEquals(500, retry.getRetryInterval(2));
    assertEquals(1000, retry.getRetryInterval(3));
  }

}
