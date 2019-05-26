package se.yolean.kafka.keyvalue.onupdate;

import static org.junit.jupiter.api.Assertions.*;

import java.io.UnsupportedEncodingException;
import java.util.Map;

import org.junit.jupiter.api.Test;

import se.yolean.kafka.keyvalue.UpdateRecord;

class UpdatesBodyPerTopicJSONTest {

  @Test
  void testEmpty() throws UnsupportedEncodingException {
    UpdatesBodyPerTopicJSON body = new UpdatesBodyPerTopicJSON("t");
    assertEquals("{\"v\":1,\"topic\":\"t\",\"offsets\":{},\"updates\":{}}", body.getContent());
    Map<String, String> headers = body.getHeaders();
    assertEquals("t", headers.get(UpdatesBodyPerTopic.HEADER_TOPIC));
    assertEquals("", headers.get(UpdatesBodyPerTopic.HEADER_OFFSETS));
  }

  @Test
  void test1() throws UnsupportedEncodingException {
    UpdatesBodyPerTopicJSON body = new UpdatesBodyPerTopicJSON("t1");
    body.handle(new UpdateRecord("t", 1, 3, "k1"));
    assertEquals("{\"v\":1,\"topic\":\"t1\",\"offsets\":{\"1\":3},\"updates\":{\"k1\":{}}}", body.getContent());
    Map<String, String> headers = body.getHeaders();
    assertEquals("t1", headers.get(UpdatesBodyPerTopic.HEADER_TOPIC));
    assertEquals("1=3", headers.get(UpdatesBodyPerTopic.HEADER_OFFSETS));
  }

  @Test
  void test2() throws UnsupportedEncodingException {
    UpdatesBodyPerTopicJSON body = new UpdatesBodyPerTopicJSON("t2");
    body.handle(new UpdateRecord("t", 0, 10, "k1"));
    body.handle(new UpdateRecord("t", 0, 11, "k2"));
    assertEquals("{\"v\":1,\"topic\":\"t2\",\"offsets\":{\"0\":11},\"updates\":{\"k1\":{},\"k2\":{}}}", body.getContent());
  }

}
