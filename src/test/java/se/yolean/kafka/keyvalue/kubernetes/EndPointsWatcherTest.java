package se.yolean.kafka.keyvalue.kubernetes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.client.Watcher.Action;
import se.yolean.kafka.keyvalue.UpdateRecord;
import se.yolean.kafka.keyvalue.onupdate.UpdatesBodyPerTopicJSON;

public class EndPointsWatcherTest {

  EndpointAddress createEndpoint(String ip, String targetName) {
    var targetRef = new ObjectReference();
    targetRef.setName(targetName);

    return new EndpointAddress("hostnamethatwedontuse", ip, "nodenamethatwedontuse", targetRef);
  }

  @Test
  public void unreadyEndpointsSequence() {
    var watcher = new EndpointsWatcher();

    List<EndpointAddress> notReadyAddresses = List.of(
      createEndpoint("192.168.0.1", "pod1"),
      createEndpoint("192.168.0.2", "pod2")
    );
    List<EndpointAddress> addresses = List.of();

    var endpoints = new Endpoints();
    endpoints.setSubsets(List.of(new EndpointSubset(addresses, notReadyAddresses, null)));

    watcher.handleEvent(Action.MODIFIED, endpoints);

    assertEquals(Map.of("192.168.0.1", "pod1", "192.168.0.2", "pod2"), watcher.getUnreadyTargets());
    assertEquals(Map.of(), watcher.getTargets());

    List<EndpointAddress> notReadyAddresses2 = List.of(
      createEndpoint("192.168.0.2", "pod2"),
      createEndpoint("192.168.0.3", "pod3")
    );
    List<EndpointAddress> addresses2 = List.of(
      createEndpoint("192.168.0.1", "pod1")
    );

    endpoints.setSubsets(List.of(new EndpointSubset(addresses2, notReadyAddresses2, null)));

    watcher.handleEvent(Action.MODIFIED, endpoints);

    assertEquals(Map.of("192.168.0.2", "pod2", "192.168.0.3", "pod3"), watcher.getUnreadyTargets());
    assertEquals(Map.of("192.168.0.1", "pod1"), watcher.getTargets());
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

    watcher.handleEvent(Action.MODIFIED, endpoints);

    assertEquals(Map.of("192.168.0.1", "pod1", "192.168.0.2", "pod2"), watcher.getUnreadyTargets());
    assertEquals(Map.of(), watcher.getTargets());


    List<EndpointAddress> notReadyAddresses2 = List.of(
      createEndpoint("192.168.0.3", "pod3")
    );
    List<EndpointAddress> addresses2 = List.of(
      createEndpoint("192.168.0.1", "pod1")
    );
    endpoints.setSubsets(List.of(new EndpointSubset(addresses2, notReadyAddresses2, null)));

    watcher.handleEvent(Action.MODIFIED, endpoints);

    assertEquals(Map.of("192.168.0.3", "pod3"), watcher.getUnreadyTargets());
    assertEquals(Map.of("192.168.0.1", "pod1"), watcher.getTargets());

    var update = new UpdatesBodyPerTopicJSON("mytopic");
    update.handle(new UpdateRecord("mytopic", 0, 3, "key1", 0));

    watcher.updateUnreadyTargets(update);

    var receivedUpdates = new ArrayList<>();
    watcher.addOnReadyConsumer((body, target) -> {
      receivedUpdates.add(body);
      receivedUpdates.add(target);
    });

    List<EndpointAddress> notReadyAddresses3 = List.of();
    List<EndpointAddress> addresses3 = List.of(
      createEndpoint("192.168.0.1", "pod1"),
      createEndpoint("192.168.0.3", "pod3")
    );
    endpoints.setSubsets(List.of(new EndpointSubset(addresses3, notReadyAddresses3, null)));

    watcher.handleEvent(Action.MODIFIED, endpoints);

    assertEquals(receivedUpdates, List.of(update, Map.of("192.168.0.3", "pod3")));
    assertEquals(Map.of(), watcher.getUnreadyTargets());
    assertEquals(Map.of("192.168.0.1", "pod1", "192.168.0.3", "pod3"), watcher.getTargets());
  }

}
