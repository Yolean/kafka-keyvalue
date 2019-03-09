package se.yolean.kafka.keyvalue.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import se.yolean.kafka.keyvalue.CacheServiceOptions;
import se.yolean.kafka.keyvalue.onupdate.OnUpdateWithExternalPollTrigger;

class ArgsToOptionsTest {

  @Test
  void test() {
    String args = "--port 19081"
        + " --streams-props bootstrap.servers=localhost:19092 num.standby.replicas=0"
        + " --hostname mypod-abcde"
        + " --topic topic1"
        + " --application-id kv-test1-001"
        + " --onupdate http://127.0.0.1:8081/updated"
        + " --starttimeout 15";
    CacheServiceOptions options = new ArgsToOptions(args.split("\\s+")) {
      @Override
      protected OnUpdateWithExternalPollTrigger newOnUpdate(List<String> onupdateUrls, Integer onupdateTimeout,
          Integer onupdateRetries) {
        assertEquals(1, onupdateUrls.size());
        assertEquals("http://127.0.0.1:8081/updated", onupdateUrls.get(0));
        assertEquals(DEFAULT_ONUPDATE_TIMEOUT, onupdateTimeout);
        assertEquals(DEFAULT_ONUPDATE_RETRIES, onupdateRetries);
        return Mockito.mock(OnUpdateWithExternalPollTrigger.class);
      }
    };
    assertEquals(19081, options.getPort());
    //assertEquals("mypod-abcde", options.getHostname());
    assertEquals("kv-test1-001", options.getApplicationId());
    Properties props = options.getStreamsProperties();
    assertEquals("localhost:19092", props.get("bootstrap.servers"));
    assertEquals("0", props.get("num.standby.replicas"));
    assertEquals(15, options.getStartTimeoutSecods());
    assertNotNull(options.getOnUpdate());
  }

  @Test
  void testOnupdateMany() {
    String args = "--port 19082"
        + " --streams-props bootstrap.servers=localhost:19092"
        + " --topic topic2"
        + " --application-id kv-test1-001"
        + " --onupdate http://127.0.0.1:8081/updated http://127.0.0.1:8082/updates"
        //+ " --onupdate-retries 4"
        + " --onupdate-timeout 13000";
    CacheServiceOptions options = new ArgsToOptions(args.split("\\s+")) {
      @Override
      protected OnUpdateWithExternalPollTrigger newOnUpdate(List<String> onupdateUrls, Integer onupdateTimeout,
          Integer onupdateRetries) {
        assertEquals(2, onupdateUrls.size());
        assertEquals("http://127.0.0.1:8081/updated", onupdateUrls.get(0));
        assertEquals("http://127.0.0.1:8082/updates", onupdateUrls.get(1));
        assertEquals(13000, onupdateTimeout);
        assertEquals(DEFAULT_ONUPDATE_RETRIES, onupdateRetries);
        return Mockito.mock(OnUpdateWithExternalPollTrigger.class);
      }
    };
    assertNotNull(options.getOnUpdate());
    assertFalse(options.getStandalone());
  }

  @Test
  void testStandalone() {
    String args = "--standalone"
        + " --hostname pod-12345678-abcde"
        + " --application-id kv-"
        + " --streams-props bootstrap.servers=bootstrap.kafka:9092"
        + " --topic topic3"
        + " --onupdate http://myservice/updates";
    CacheServiceOptions options = new ArgsToOptions(args.split("\\s+"));
    assertTrue(options.getStandalone());
    assertTrue(options.getApplicationId().length() > 22,
        "Standalone mode should append to application id");
    assertEquals("kv-pod-12345678-abcde-", options.getApplicationId().substring(0, 22),
        "Standalone mode should append hostname and timestamp to application id");
    assertTrue(Pattern.matches(".*-abcde-20[0-9]{6}t[0-1][0-9]{5}$", options.getApplicationId()),
        "Timestamp should be around now, got " + options.getApplicationId());
  }

}
