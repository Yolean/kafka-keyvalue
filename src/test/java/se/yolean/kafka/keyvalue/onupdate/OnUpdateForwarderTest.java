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

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import se.yolean.kafka.keyvalue.UpdateRecord;

class OnUpdateForwarderTest {

  @Test
  void testGetTargetsConfig() {
    OnUpdateForwarder forwarder = new OnUpdateForwarder();

    forwarder.target = Optional.empty();
    forwarder.target1 = Optional.empty();
    forwarder.target2 = Optional.empty();
    forwarder.target3 = Optional.empty();
    forwarder.target4 = Optional.empty();
    forwarder.target5 = Optional.empty();
    forwarder.target6 = Optional.empty();
    forwarder.target7 = Optional.empty();
    forwarder.target8 = Optional.empty();
    forwarder.target9 = Optional.empty();

    assertNotNull(forwarder.getTargetsConfig());
    assertEquals(0, forwarder.getTargetsConfig().size());

    forwarder.target = Optional.of("http://example.net/");
    assertEquals(1, forwarder.getTargetsConfig().size());
    assertEquals("http://example.net/", forwarder.getTargetsConfig().get(0));
  }

  @Test
  void testNoStart() {
    OnUpdateForwarder forwarder = new OnUpdateForwarder();
    forwarder.target = Optional.of("testtopic");
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
    forwarder.target = Optional.of("testtopic");
    forwarder.pollStart(Collections.singleton("testtopic"));
    try {
      forwarder.pollStart(Collections.singleton("testtopic"));
      fail("Should have thrown");
    } catch (IllegalStateException e) {
      assertEquals("pollStart called twice without pollEnd", e.getMessage());
    }
  }

  @Test
  @Disabled // had not time to complete the mockig here
  void testZeroUpdates() {
    OnUpdateForwarder forwarder = new OnUpdateForwarder();
    forwarder.target = Optional.of("testtopic");
    forwarder.target1 = Optional.empty();
    forwarder.target2 = Optional.empty();
    forwarder.target3 = Optional.empty();
    forwarder.target4 = Optional.empty();
    forwarder.target5 = Optional.empty();
    forwarder.target6 = Optional.empty();
    forwarder.target7 = Optional.empty();
    forwarder.target8 = Optional.empty();
    forwarder.target9 = Optional.empty();
    forwarder.dispatcherConfig = Mockito.mock(DispatcherConfig.class);
    UpdatesBodyPerTopic handler = Mockito.mock(UpdatesBodyPerTopic.class);
    Mockito.when(forwarder.dispatcherConfig.getUpdatesHandlerForPoll("testtopic")).thenReturn(handler );
    forwarder.start(null);
    forwarder.pollStart(Collections.singleton("testtopic"));
    UpdateRecord u1 = new UpdateRecord("testtopic", 0, 0, "k1");
    forwarder.pollStart(Collections.singleton("testtopic"));
    forwarder.handle(u1);
    try {
      forwarder.pollEndBlockingUntilTargetsAck();
    } catch (IllegalStateException e) {
      assertEquals("Zero handle(UpdateRecord) calls between pollStart and pollEnd", e.getMessage());
    }
  }

}
