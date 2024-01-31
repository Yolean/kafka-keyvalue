package se.yolean.kafka.keyvalue.kubernetes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsList;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import se.yolean.kafka.keyvalue.UpdateRecord;
import se.yolean.kafka.keyvalue.onupdate.UpdatesBodyPerTopicJSON;

public class EndpointsWatcherTest {

  EndpointAddress createEndpoint(String ip, String targetName) {
    var targetRef = new ObjectReference();
    targetRef.setName(targetName);

    return new EndpointAddress("hostnamethatwedontuse", ip, "nodenamethatwedontuse", targetRef);
  }

  @Test
  public void watchDisabledTest() {
    interface MixedOperationMock extends MixedOperation<Endpoints, EndpointsList, Resource<Endpoints>> {}
    MixedOperation<Endpoints, EndpointsList, Resource<Endpoints>> mixedOperationMock = mock(MixedOperationMock.class);
    interface ResourceMock extends Resource<Endpoints> {}
    Resource<Endpoints> resourceMock = mock(ResourceMock.class);
    when(mixedOperationMock.withName(any())).thenReturn(resourceMock);
    when(resourceMock.watch(any())).thenReturn(mock(Watch.class));
    var watcher = new EndpointsWatcher(new EndpointsWatcherConfig() {

      @Override
      public Optional<String> targetServiceName() {
        return Optional.empty();
      }

      @Override
      public String namespace() {
        return "dev";
      }

      @Override
      public Duration resyncPeriod() {
        return Duration.ofMinutes(5);
      }

    }, new SimpleMeterRegistry());
    watcher.client = mock(KubernetesClient.class);
    when(watcher.client.endpoints()).thenReturn(mixedOperationMock);

    watcher.start(null);

    verify(watcher.client, times(0)).endpoints();
    verify(mixedOperationMock, times(0)).withName(any());
    verify(resourceMock, times(0)).watch(any());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void watchEnabledTest() {
    interface MixedOperationMock extends MixedOperation<Endpoints, EndpointsList, Resource<Endpoints>> {}
    MixedOperation<Endpoints, EndpointsList, Resource<Endpoints>> mixedOperationMock = mock(MixedOperationMock.class);
    interface ResourceMock extends Resource<Endpoints> {}
    Resource<Endpoints> resourceMock = mock(ResourceMock.class);
    NonNamespaceOperation<Endpoints, EndpointsList, Resource<Endpoints>> nonNamespaceOperationMock = mock(NonNamespaceOperation.class);
    when(mixedOperationMock.inNamespace(any())).thenReturn(nonNamespaceOperationMock);
    when(nonNamespaceOperationMock.withName(any())).thenReturn(resourceMock);
    when(resourceMock.inform(any())).thenReturn(mock(SharedIndexInformer.class));
    var watcher = new EndpointsWatcher(new EndpointsWatcherConfig() {

      @Override
      public Optional<String> targetServiceName() {
        return Optional.of("target-service-name");
      }

      @Override
      public String namespace() {
        return "dev";
      }

      @Override
      public Duration resyncPeriod() {
        return Duration.ofMinutes(5);
      }

    }, new SimpleMeterRegistry());
    watcher.client = mock(KubernetesClient.class);
    when(watcher.client.endpoints()).thenReturn(mixedOperationMock);

    watcher.start(null);

    ArgumentCaptor<Long> resyncPeriodCaptor = ArgumentCaptor.forClass(Long.class);
    // ArgumentCaptor<String> targetServiceNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(watcher.client, times(1)).endpoints();
    verify(nonNamespaceOperationMock, times(1)).withName("target-service-name");
    verify(mixedOperationMock, times(1)).inNamespace("dev");
    // assertEquals("target-service-name", targetServiceNameCaptor.getValue());
    verify(resourceMock, times(1)).inform(any(), resyncPeriodCaptor.capture());
    assertEquals(5 * 60 * 1000, resyncPeriodCaptor.getValue());
  }

  @Test
  public void unreadyEndpointsSequence() {
    var watcher = new EndpointsWatcher(new EndpointsWatcherConfig() {

      @Override
      public Optional<String> targetServiceName() {
        return Optional.empty();
      }

      @Override
      public String namespace() {
        return "dev";
      }

      @Override
      public Duration resyncPeriod() {
        return Duration.ofMinutes(5);
      }

    }, new SimpleMeterRegistry());

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
    var watcher = new EndpointsWatcher(new EndpointsWatcherConfig() {

      @Override
      public Optional<String> targetServiceName() {
        return Optional.empty();
      }

      @Override
      public String namespace() {
        return "dev";
      }

      @Override
      public Duration resyncPeriod() {
        return Duration.ofMinutes(5);
      }

    }, new SimpleMeterRegistry());

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
