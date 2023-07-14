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

  EndpointAddress createEndpoint(String ip, String targetName) {
    var targetRef = new ObjectReference();
    targetRef.setName(targetName);

    return new EndpointAddress("hostnamethatwedontuse", ip, "nodenamethatwedontuse", targetRef);
  }

  @Test
  public void endpointsUpdateTest() {
    var watcher = new EndpointsWatcher();

    List<EndpointAddress> notReadyAddresses = List.of(
      createEndpoint("192.168.0.1", "pod1"),
      createEndpoint("192.168.0.2", "pod2")
    );
    List<EndpointAddress> addresses = List.of();

    var endpoints = new Endpoints();
    endpoints.setSubsets(List.of(new EndpointSubset(addresses, notReadyAddresses, null)));

    watcher.addOnReadyConsumer((update, target) -> {

    });

    watcher.handleEvent(Action.MODIFIED, endpoints);

    assertEquals(Map.of("192.168.0.1", "pod1", "192.168.0.2", "pod2"), watcher.getUnreadyTargets());
  }

}
