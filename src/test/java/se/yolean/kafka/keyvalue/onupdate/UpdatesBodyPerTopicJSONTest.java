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

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import se.yolean.kafka.keyvalue.UpdateRecord;

class UpdatesBodyPerTopicJSONTest {

  @Test
  void testMerge() {
    UpdatesBodyPerTopicJSON body = new UpdatesBodyPerTopicJSON("topic");
    body.handle(new UpdateRecord("t", 0, 10, "k1"));
    body.handle(new UpdateRecord("t", 0, 11, "k2"));

    UpdatesBodyPerTopicJSON body2 = new UpdatesBodyPerTopicJSON("topic");
    body2.handle(new UpdateRecord("t", 1, 14, "k2"));
    body2.handle(new UpdateRecord("t", 0, 17, "k1"));

    UpdatesBodyPerTopicJSON body3 = new UpdatesBodyPerTopicJSON("topic");
    body3.handle(new UpdateRecord("t", 0, 6, "k3"));

    var result = UpdatesBodyPerTopic.merge(List.of(
      body,
      body2,
      body3
    ));

    result.getHeaders();

    assertEquals(
      "{\"v\":1,\"topic\":\"topic\",\"offsets\":{\"0\":17,\"1\":14},\"updates\":{\"k1\":{},\"k2\":{},\"k3\":{}}}",
      new String(result.getContent())
    );
  }

  @Test
  void testEmpty() throws UnsupportedEncodingException {
    UpdatesBodyPerTopicJSON body = new UpdatesBodyPerTopicJSON("t");
    Map<String, String> headers = body.getHeaders();
    assertEquals(
        "{\"v\":1,\"topic\":\"t\",\"offsets\":{},\"updates\":{}}",
        new String(body.getContent()));
    assertEquals("application/json", body.getContentType());
    assertEquals("t", headers.get(UpdatesBodyPerTopic.HEADER_TOPIC));
    assertEquals("{}", headers.get(UpdatesBodyPerTopic.HEADER_OFFSETS));
  }

  @Test
  void test1() throws UnsupportedEncodingException {
    UpdatesBodyPerTopicJSON body = new UpdatesBodyPerTopicJSON("t1");
    body.handle(new UpdateRecord("t", 1, 3, "k1"));
    Map<String, String> headers = body.getHeaders();
    ByteArrayOutputStream content = new ByteArrayOutputStream();
    body.getContent(content);
    assertEquals(
        "{\"v\":1,\"topic\":\"t1\",\"offsets\":{\"1\":3},\"updates\":{\"k1\":{}}}",
        new String(content.toByteArray()));
    assertEquals("t1", headers.get(UpdatesBodyPerTopic.HEADER_TOPIC));
    assertEquals("{\"1\":3}", headers.get(UpdatesBodyPerTopic.HEADER_OFFSETS));
  }

  @Test
  void test2() throws UnsupportedEncodingException {
    UpdatesBodyPerTopicJSON body = new UpdatesBodyPerTopicJSON("t2");
    body.handle(new UpdateRecord("t", 0, 10, "k1"));
    body.handle(new UpdateRecord("t", 0, 11, "k2"));
    body.getHeaders();
    assertEquals(
        "{\"v\":1,\"topic\":\"t2\",\"offsets\":{\"0\":11},\"updates\":{\"k1\":{},\"k2\":{}}}",
        new String(body.getContent()));
  }

  @Test
  void testHeadersAfterContent() {
    UpdatesBodyPerTopicJSON body = new UpdatesBodyPerTopicJSON("t2");
    try {
      body.getContent(new ByteArrayOutputStream());
    } catch (IllegalStateException e) {
      assertEquals("Headers must be retrieved before body", e.getMessage());
    }
  }

  @Test
  public void testNullKey() {
    UpdatesBodyPerTopicJSON body = new UpdatesBodyPerTopicJSON("t");
    try {
      body.handle(new UpdateRecord("t", 0, 1234, null));
    } catch (IllegalArgumentException e) {
      assertEquals("Null key rejected, partition 0 offset 1234", e.getMessage());
    }
  }

}
