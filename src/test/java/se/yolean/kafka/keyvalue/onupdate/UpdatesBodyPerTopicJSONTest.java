package se.yolean.kafka.keyvalue.onupdate;

import static org.junit.jupiter.api.Assertions.*;

import java.io.UnsupportedEncodingException;

import org.junit.jupiter.api.Test;

import se.yolean.kafka.keyvalue.UpdateRecord;

class UpdatesBodyPerTopicJSONTest {

  @Test
  void testEmpty() throws UnsupportedEncodingException {
    UpdatesBodyPerTopicJSON body = new UpdatesBodyPerTopicJSON();
    assertEquals("{\"v\":1,\"offsets\":{},\"updates\":{}}", body.getContent());
  }

  @Test
  void test1() throws UnsupportedEncodingException {
    UpdatesBodyPerTopicJSON body = new UpdatesBodyPerTopicJSON();
    body.handle(new UpdateRecord("t", 1, 3, "k1"));
    assertEquals("{\"v\":1,\"offsets\":{\"1\":3},\"updates\":{\"k1\":{}}}", body.getContent());
  }

  @Test
  void test2() throws UnsupportedEncodingException {
    UpdatesBodyPerTopicJSON body = new UpdatesBodyPerTopicJSON();
    body.handle(new UpdateRecord("t", 0, 10, "k1"));
    body.handle(new UpdateRecord("t", 0, 11, "k2"));
    assertEquals("{\"v\":1,\"offsets\":{\"0\":11},\"updates\":{\"k1\":{},\"k2\":{}}}", body.getContent());
  }

}
