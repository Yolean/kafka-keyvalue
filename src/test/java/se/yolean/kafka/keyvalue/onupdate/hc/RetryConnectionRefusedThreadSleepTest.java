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
