package se.yolean.kafka.keyvalue;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Random;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import se.yolean.kafka.keyvalue.http.CacheServer;
import se.yolean.kafka.keyvalue.http.ConfigureRest;

class EndpointsIntegrationTest {

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
  void testCacheAccess() {
    CacheServiceOptions options = Mockito.mock(CacheServiceOptions.class);
    Mockito.when(options.getPort()).thenReturn(port);
    KafkaCache cache = Mockito.mock(KafkaCache.class);
    Endpoints endpoints = new Endpoints(cache);
    server = new ConfigureRest()
        .createContext(options.getPort(), "/")
        .registerResourceInstance(endpoints)
        .asServlet()
        .create();
    server.start();

    Response r0 = client.target(root + "/cache/v1/raw/key01").request().get();
    assertEquals(404, r0.getStatus());
    Mockito.when(cache.getValue("key01")).thenReturn("value01".getBytes());
    Response r1 = client.target(root + "/cache/v1/raw/key01").request().get();
    assertEquals(200, r1.getStatus());
    assertEquals("value01", r1.readEntity(String.class));

    Mockito.when(cache.getKeys()).thenReturn(Arrays.asList("k1", "k2").iterator());
    Response keys = client.target(root + "/cache/v1/keys").request().get();
    assertEquals(200, keys.getStatus());
    assertEquals("[\"k1\",\"k2\"]", keys.readEntity(String.class));

    Mockito.when(cache.getValues()).thenReturn(Arrays.asList("v1".getBytes(), "v2".getBytes()).iterator());
    Response values = client.target(root + "/cache/v1/values").request().get();
    assertEquals(200, values.getStatus());
    assertEquals("v1" + "\n" + "v2" + "\n", values.readEntity(String.class));

    Mockito.when(cache.getCurrentOffset("topic2", 2)).thenReturn(123L);
    Response offset = client.target(root + "/cache/v1/offset/topic2/2").request().get();
    assertEquals(200, offset.getStatus());
    assertEquals("application/json", offset.getHeaderString("Content-Type"));
    assertEquals("123", offset.readEntity(String.class));
  }

}
