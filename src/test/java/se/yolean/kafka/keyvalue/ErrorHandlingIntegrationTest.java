package se.yolean.kafka.keyvalue;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import se.yolean.kafka.keyvalue.ConsumerAtLeastOnce.Stage;

public class ErrorHandlingIntegrationTest {

  private String bootstrap;

  @BeforeEach
  void before() throws UnknownHostException, IOException {
    ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    bootstrap = server.getInetAddress().getHostAddress() + ":" + server.getLocalPort();
    server.close();
  }

  @AfterEach
  void after() throws IOException {
  }

  Properties getConsumerProperties(String bootstrap, String groupId) {
    Properties props = new Properties();
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    // use latest here to allow many test cases to use the same topic (though all test cases keys' will be read to cache)
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "none");
    return props;
  }

  @Test
  void testMetadataTimeout() throws InterruptedException, ExecutionException {

    ConsumerAtLeastOnce consumer = new ConsumerAtLeastOnce();
    final String TOPIC = "topic1";
    final String GROUP = this.getClass().getSimpleName() + "_testMetadataTimeout_" + System.currentTimeMillis();
    final String BOOTSTRAP = bootstrap;

    consumer.consumerProps = getConsumerProperties(BOOTSTRAP, GROUP);
    consumer.onupdate = Mockito.mock(OnUpdate.class);
    consumer.cache = new HashMap<>();
    consumer.topics = Collections.singletonList(TOPIC);

    consumer.maxPolls = 5;
    consumer.metadataTimeout = Duration.ofMillis(10);
    consumer.pollDuration = Duration.ofMillis(3000);
    consumer.minPauseBetweenPolls = Duration.ofMillis(2000);

    long t1 = System.currentTimeMillis();
    try {
      consumer.run();
      fail("Should have thrown");
    } catch (RuntimeException e) {
      assertEquals("Timeout expired while fetching topic metadata", e.getMessage());
      assertEquals(org.apache.kafka.common.errors.TimeoutException.class, e.getClass());
    }
    assertTrue(System.currentTimeMillis() - t1 > 10, "Should have spent time waiting for metadata timeout");
    assertTrue(System.currentTimeMillis() - t1 < 1900, "Should have exited after metadata timeout, not waited for other things");

    assertFalse(consumer.isReady(),
        "Should have stopped at an exception (tests don't use a thread so this is probably a dummy assertion)");
    assertEquals(Stage.WaitingForKafkaConnection, consumer.stage,
        "Should have exited at the initial kafka connection stage");
  }

  @Test
  void testIsAlive() throws InterruptedException {
    ConsumerAtLeastOnce consumer = new ConsumerAtLeastOnce() {
      @Override
      public void run() {
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) { fail(e); }
      }
    };
    consumer.shouldBeRunning = true;
    consumer.startIfNeeded();
    assertTrue(consumer.runner.isAlive());
    Thread.sleep(20);
    assertFalse(consumer.runner.isAlive());
  }

  @Test
  void testIsAliveAfterException() throws InterruptedException {
    ConsumerAtLeastOnce consumer = new ConsumerAtLeastOnce() {
      @Override
      public void run() {
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) { fail(e); }
        throw new org.apache.kafka.common.errors.TimeoutException();
      }
    };
    consumer.shouldBeRunning = true;
    consumer.startIfNeeded();
    assertTrue(consumer.runner.isAlive());
    Thread.sleep(20);
    assertFalse(consumer.runner.isAlive());
  }


  @Test
  void testFailToConnectAsThread() throws InterruptedException, ExecutionException {

    final List<Object> runs = new LinkedList<Object>();

    // Based on that this behavior is asserted above
    RuntimeException error = new org.apache.kafka.common.errors.TimeoutException();
    ConsumerAtLeastOnce consumer = new ConsumerAtLeastOnce() {
      @Override void topicsFromConfig() {}
      @Override
      public void run() {
        runs.add(null);
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          fail(e);
        }
        throw error;
      }
    };

    final String TOPIC = "topic1";
    final String GROUP = this.getClass().getSimpleName() + "_testFailToConnectAsThread_" + System.currentTimeMillis();
    final String BOOTSTRAP = bootstrap;
    final long TIMEOUT = 100;

    consumer.consumerProps = getConsumerProperties(BOOTSTRAP, GROUP);
    consumer.onupdate = Mockito.mock(OnUpdate.class);
    consumer.cache = new HashMap<>();
    consumer.topics = Collections.singletonList(TOPIC);

    consumer.maxPolls = 5;
    consumer.metadataTimeout = Duration.ofMillis(TIMEOUT);
    consumer.pollDuration = Duration.ofMillis(1000);
    consumer.minPauseBetweenPolls = Duration.ofMillis(500);

    StartupEvent startup = Mockito.mock(StartupEvent.class);
    consumer.start(startup);
    Thread.sleep(TIMEOUT / 2);
    assertEquals(1, runs.size());
    Thread.sleep(TIMEOUT);

    consumer.call(); // Readiness probe
    Thread.sleep(50);
    assertEquals(2, runs.size());
    Thread.sleep(TIMEOUT);

    consumer.call(); // Readiness probe
    Thread.sleep(TIMEOUT / 2);
    assertEquals(3, runs.size());
    Thread.sleep(TIMEOUT);

    ShutdownEvent shutdown = Mockito.mock(ShutdownEvent.class);
    consumer.stop(shutdown);

    consumer.call(); // Readiness probe
    Thread.sleep(TIMEOUT / 2);
    assertEquals(3, runs.size());
    Thread.sleep(TIMEOUT);

    consumer.call(); // Readiness probe
    Thread.sleep(TIMEOUT / 2);
    assertEquals(3, runs.size());
    Thread.sleep(TIMEOUT);
  }

}
