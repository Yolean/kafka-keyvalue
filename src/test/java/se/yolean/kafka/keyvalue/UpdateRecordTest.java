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

package se.yolean.kafka.keyvalue;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UpdateRecordTest {

  @Test
  void testToString() {
    assertEquals("t1-123-45678[kx]", new UpdateRecord("t1", 123, 45678, "kx").toString());
  }

  @Test
  void testHashCode() {
    assertEquals("t1-123-45678[kx]".hashCode(), new UpdateRecord("t1", 123, 45678, "kx").hashCode());
  }

  @Test
  void testGetTopicPartition() {
    UpdateRecord u = new UpdateRecord("t1", 123, 45678, "kx");
    assertEquals("t1", u.getTopicPartition().topic());
    assertEquals(123, u.getTopicPartition().partition());
    assertEquals("t1-123", u.getTopicPartition().toString());
    assertTrue(u.getTopicPartition() == u.getTopicPartition());
  }

  @Test
  void testEquals() {
    assertTrue(new UpdateRecord("1", 1, 2, "x").equals(new UpdateRecord("1", 1, 2, "x")));
    assertFalse(new UpdateRecord("1", 1, 2, "x").equals(new UpdateRecord("1", 1, 2, "x2")));
    assertFalse(new UpdateRecord("1", 1, 2, "x").equals(new UpdateRecord("1", 12, 2, "x")));
  }

  /* We no longer exchange these objects over HTTP so jackson annotations were removed
  @Test
  void testJSON() throws IOException {
    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    String json = mapper.writeValueAsString(new UpdateRecord("t2", 12, 345, "my key"));
    assertEquals("{\"topic\":\"t2\",\"partition\":12,\"offset\":345,\"key\":\"my key\"}", json);

    UpdateRecord u = mapper.readValue("{\"topic\":\"t3\",\"partition\":22,\"offset\":432,\"key\":\"my other key\"}", UpdateRecord.class);
    assertEquals("t3", u.getTopic());
    assertEquals(22, u.getPartition());
    assertEquals(432, u.getOffset());
    assertEquals("my other key", u.getKey());
  }
  */

}
