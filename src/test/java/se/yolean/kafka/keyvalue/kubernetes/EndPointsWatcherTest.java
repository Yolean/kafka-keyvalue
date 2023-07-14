package se.yolean.kafka.keyvalue.kubernetes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.client.Watcher.Action;

public class EndPointsWatcherTest {

  @Test
  public void endpointsUpdateTest() {
    var watcher = new EndpointsWatcher();

    var targetRef = new ObjectReference();
    targetRef.setName("targetRef");
    var addresses = List.of(new EndpointAddress("hostname", "127.0.0.1", "nodename", targetRef));
    var notReadyAddresses = List.of(new EndpointAddress("hostname", "127.0.0.1", "nodename", targetRef));

    var endpoints = new Endpoints();
    endpoints.setSubsets(List.of(new EndpointSubset(addresses, notReadyAddresses, null)));


    watcher.addOnReadyConsumer((update, target) -> {

    });

    watcher.handleEvent(Action.MODIFIED, endpoints);

    assertEquals(Map.of("foo", "bar"), watcher.getUnreadyTargets());
    assertEquals(Map.of("foo", "bar"), watcher.getTargets());
  }

}
