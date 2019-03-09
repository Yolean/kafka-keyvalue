package se.yolean.kafka.keyvalue.http;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import se.yolean.kafka.keyvalue.CacheServiceOptions;
import se.yolean.kafka.keyvalue.KafkaCache;
import se.yolean.kafka.keyvalue.Readiness;

class JettyCacheServerIntegrationTest {

  private CacheServer server = null;
  private int port;
  private String root;
  private Client client;

  @BeforeEach
  void setUp() {
    Random random = new Random();
    port = 58000 + random.nextInt(1000);
    root = "http://127.0.0.1:" + port;
    client = ClientBuilder.newBuilder().build();
  }

  @AfterEach
  void tearDown() throws Exception {
    if (server != null) {
      server.stop();
    }
    server = null;
  }

  @Test
  void testSameSetupAsInApp() {
    CacheServiceOptions options = Mockito.mock(CacheServiceOptions.class);
    Mockito.when(options.getPort()).thenReturn(port);
    Readiness readiness = Mockito.mock(Readiness.class);
    TestEndpoints endpoints = new TestEndpoints();
    server = new ConfigureRest()
        .createContext(options.getPort(), "/")
        .registerResourceClass(org.glassfish.jersey.jackson.JacksonFeature.class)
        .registerResourceInstance(endpoints)
        .asServlet()
        .addCustomServlet(new ReadinessServlet().setReadiness(readiness), "/healthz")
        .create();
    server.start();

    Response r1 = client.target(root + "/cache/v1/k1").request().get();
    assertEquals(200, r1.getStatus());
    assertEquals("v-k1", r1.readEntity(String.class));

    Response r2 = client.target(root + "/cache/v1/null").request().get();
    assertEquals(404, r2.getStatus());

    Mockito.when(readiness.isAppReady()).thenReturn(false);
    Response unready = client.target(root + "/healthz").request().get();
    assertEquals(500, unready.getStatus());
    Mockito.when(readiness.isAppReady()).thenReturn(true);
    Response ready = client.target(root + "/healthz").request().get();
    assertEquals(200, ready.getStatus());
  }

}
