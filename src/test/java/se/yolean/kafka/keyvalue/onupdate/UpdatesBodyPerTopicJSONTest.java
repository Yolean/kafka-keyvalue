package se.yolean.kafka.keyvalue.onupdate;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Map;

import org.junit.jupiter.api.Test;

import se.yolean.kafka.keyvalue.UpdateRecord;

class UpdatesBodyPerTopicJSONTest {

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
    assertEquals(
        "{\"v\":1,\"topic\":\"t2\",\"offsets\":{\"0\":11},\"updates\":{\"k1\":{},\"k2\":{}}}",
        new String(body.getContent()));
  }

  @Test
  void testContentTwice() {
    UpdatesBodyPerTopicJSON body = new UpdatesBodyPerTopicJSON("t2");
    body.getContent();
    try {
      body.getContent();
    } catch (IllegalStateException e) {
      assertEquals("Refusing to serialize content twice", e.getMessage());
    }
  }

  @Test
  void testContentTwiceStream() {
    UpdatesBodyPerTopicJSON body = new UpdatesBodyPerTopicJSON("t2");
    body.getContent();
    try {
      body.getContent(new ByteArrayOutputStream());
    } catch (IllegalStateException e) {
      assertEquals("Refusing to serialize content twice", e.getMessage());
    }
  }

  @Test
  void testContentTwiceStreamFirst() {
    UpdatesBodyPerTopicJSON body = new UpdatesBodyPerTopicJSON("t2");
    body.getContent(new ByteArrayOutputStream());
    try {
      body.getContent();
    } catch (IllegalStateException e) {
      assertEquals("Refusing to serialize content twice", e.getMessage());
    }
  }

  @Test
  void testHeadersAfterContent() {
    UpdatesBodyPerTopicJSON body = new UpdatesBodyPerTopicJSON("t2");
    body.getContent(new ByteArrayOutputStream());
    try {
      body.getHeaders();
    } catch (IllegalStateException e) {
      assertEquals("Refusing to return headers after content", e.getMessage());
    }
  }

}
