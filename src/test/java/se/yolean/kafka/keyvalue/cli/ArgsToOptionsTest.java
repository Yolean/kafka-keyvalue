package se.yolean.kafka.keyvalue.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import se.yolean.kafka.keyvalue.CacheServiceOptions;
import se.yolean.kafka.keyvalue.onupdate.OnUpdateFactory;

class ArgsToOptionsTest {

  @Test
  void test() {
    String args = "--port 19081 "
        + "--streams-props bootstrap.servers=localhost:19092 num.standby.replicas=0 "
        + "--hostname mypod-abcde "
        + "--topic topic1 "
        + "--application-id kv-test1-001 "
        + "--onupdate http://127.0.0.1:8081/updated";
    OnUpdateFactory onUpdateFactory = Mockito.mock(OnUpdateFactory.class);
    ArgsToOptions a = new ArgsToOptions();
    a.setOnUpdateFactory(onUpdateFactory);
    CacheServiceOptions options = a.fromCommandLineArguments(args.split("\\s+"));
    assertEquals(19081, options.getPort());
    //assertEquals("mypod-abcde", options.getHostname());
    assertEquals("kv-test1-001", options.getApplicationId());
    Mockito.verify(onUpdateFactory).fromUrl("http://127.0.0.1:8081/updated");
    Properties props = options.getStreamsProperties();
    assertEquals("localhost:19092", props.get("bootstrap.servers"));
    assertEquals("0", props.get("num.standby.replicas"));
  }

}
