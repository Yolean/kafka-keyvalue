package se.yolean.kafka.keyvalue.onupdate.webclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.ext.web.client.WebClient;
import se.yolean.kafka.keyvalue.UpdateRecord;
import se.yolean.kafka.keyvalue.kubernetes.EndpointsWatcher;
import se.yolean.kafka.keyvalue.onupdate.UpdatesBodyPerTopic;
import se.yolean.kafka.keyvalue.onupdate.UpdatesBodyPerTopicJSON;

public class UpdatesDispatcherWebclientTest {

  private UpdatesDispatcherWebclient dispatcher;

  AutoCloseable mocks;

  MockedStatic<WebClient> webClienStatic;

  @org.mockito.Mock
  private WebClient webClient;

  @org.mockito.Mock
  private io.vertx.mutiny.ext.web.client.HttpRequest<io.vertx.mutiny.core.buffer.Buffer> mockRequest;

  @org.mockito.Mock
  private io.vertx.mutiny.ext.web.client.HttpResponse<io.vertx.mutiny.core.buffer.Buffer> mockResponse;

  @org.mockito.Mock
  private EndpointsWatcher endpointsWatcher;

  @BeforeEach
  void setup() {
    mocks = MockitoAnnotations.openMocks(this);
    webClienStatic = Mockito.mockStatic(WebClient.class);
    webClienStatic.when(() -> WebClient.create(any())).thenReturn(webClient);
    when(webClient.post(anyInt(), anyString(), anyString())).thenReturn(mockRequest);
    when(mockRequest.putHeaders(any())).thenReturn(mockRequest);
    when(mockRequest.sendJsonObject(any())).thenReturn(Uni.createFrom().item(mockResponse));

    dispatcher = new UpdatesDispatcherWebclient(null, new SimpleMeterRegistry(), endpointsWatcher);
  }

  @AfterEach
  void reset() {
    try {
      mocks.close();
      webClienStatic.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void testTargetUpdateFailureMetric() {
    var registry = new SimpleMeterRegistry();

    assertEquals("", registry.getMetersAsString());
    UpdatesDispatcherWebclient.initMetrics(registry);
    assertEquals("kkv.target.update.failure(COUNTER)[]; count=0.0", registry.getMetersAsString());
  }



  @Test
  void testDispatchToEndpoints() {
    dispatcher.config = new UpdatesDispatcherWebclientConfig() {
      @Override
      public int targetServicePort() {
        return 8080;
      }
      @Override
      public String targetPath() {
        return "/kafka-keyvalue/v1/updates";
      }
      @Override
      public Optional<String> targetStaticHost() {
        return Optional.empty();
      }
      @Override
      public int targetStaticPort() {
        return 8080;
      }
      @Override
      public long retryTimes() {
        return 1;
      }
      @Override
      public long retryBackoffSeconds() {
        return 1;
      }
    };

    when(endpointsWatcher.getTargets()).thenReturn(Map.of(
      "target-ip-1", "target-1",
      "target-ip-2", "target-2",
      "target-ip-3", "target-3"
    ));

    UpdatesBodyPerTopic updates = new UpdatesBodyPerTopicJSON("test-topic");
    updates.handle(new UpdateRecord("test-topic", 0, 0, "key1"));

    dispatcher.dispatch(updates);

    ArgumentCaptor<String> ipCaptor = ArgumentCaptor.forClass(String.class);
    verify(webClient, times(3)).post(anyInt(), ipCaptor.capture(), anyString());

    List<String> sortedCapturedValues = ipCaptor.getAllValues().stream().sorted().toList();
    assertEquals("target-ip-1", sortedCapturedValues.get(0));
    assertEquals("target-ip-2", sortedCapturedValues.get(1));
    assertEquals("target-ip-3", sortedCapturedValues.get(2));
  }

  @Test
  void testDispatchToStaticHost() {
    dispatcher.config = new UpdatesDispatcherWebclientConfig() {
      @Override
      public int targetServicePort() {
        return 8080;
      }
      @Override
      public String targetPath() {
        return "/kafka-keyvalue/v1/updates";
      }
      @Override
      public Optional<String> targetStaticHost() {
        return Optional.of("static-host");
      }
      @Override
      public int targetStaticPort() {
        return 8080;
      }
      @Override
      public long retryTimes() {
        return 1;
      }
      @Override
      public long retryBackoffSeconds() {
        return 1;
      }
    };

    when(endpointsWatcher.getTargets()).thenReturn(Map.of());

    UpdatesBodyPerTopic updates = new UpdatesBodyPerTopicJSON("test-topic");
    updates.handle(new UpdateRecord("test-topic", 0, 0, "key1"));

    dispatcher.dispatch(updates);

    ArgumentCaptor<String> ipCaptor = ArgumentCaptor.forClass(String.class);
    verify(webClient, times(1)).post(anyInt(), ipCaptor.capture(), anyString());
    assertEquals("static-host", ipCaptor.getAllValues().get(0));
  }

  @Test
  void testDispatchToEndpointsAndStaticHost() {
    dispatcher.config = new UpdatesDispatcherWebclientConfig() {
      @Override
      public int targetServicePort() {
        return 8080;
      }
      @Override
      public String targetPath() {
        return "/kafka-keyvalue/v1/updates";
      }
      @Override
      public Optional<String> targetStaticHost() {
        return Optional.of("static-host");
      }
      @Override
      public int targetStaticPort() {
        return 8080;
      }
      @Override
      public long retryTimes() {
        return 1;
      }
      @Override
      public long retryBackoffSeconds() {
        return 1;
      }
    };

    when(endpointsWatcher.getTargets()).thenReturn(Map.of(
      "target-ip-1", "target-1",
      "target-ip-2", "target-2",
      "target-ip-3", "target-3"
    ));

    UpdatesBodyPerTopic updates = new UpdatesBodyPerTopicJSON("test-topic");
    updates.handle(new UpdateRecord("test-topic", 0, 0, "key1"));

    dispatcher.dispatch(updates);

    ArgumentCaptor<String> ipCaptor = ArgumentCaptor.forClass(String.class);
    verify(webClient, times(4)).post(anyInt(), ipCaptor.capture(), anyString());
    List<String> sortedCapturedValues = ipCaptor.getAllValues().stream().sorted().toList();
    assertEquals("static-host", sortedCapturedValues.get(0));
    assertEquals("target-ip-1", sortedCapturedValues.get(1));
    assertEquals("target-ip-2", sortedCapturedValues.get(2));
    assertEquals("target-ip-3", sortedCapturedValues.get(3));
  }
}
