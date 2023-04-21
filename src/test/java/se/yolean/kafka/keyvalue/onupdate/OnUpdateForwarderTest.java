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

package se.yolean.kafka.keyvalue.onupdate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Collections;

import org.junit.jupiter.api.Test;

class OnUpdateForwarderTest {

  @Test
  void testNoStart() {
    OnUpdateForwarder forwarder = new OnUpdateForwarder();
    try {
      forwarder.pollEndBlockingUntilTargetsAck();
      fail("Should have thrown");
    } catch (IllegalStateException e) {
      assertEquals("pollEnd called without pollStart", e.getMessage());
    }
  }

  @Test
  void testTwoStarts() {
    OnUpdateForwarder forwarder = new OnUpdateForwarder();
    forwarder.pollStart(Collections.singleton("testtopic"));
    try {
      forwarder.pollStart(Collections.singleton("testtopic"));
      fail("Should have thrown");
    } catch (IllegalStateException e) {
      assertEquals("pollStart called twice without pollEnd", e.getMessage());
    }
  }

}
