package se.yolean.kafka.keyvalue;

import java.util.Collection;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.KafkaStreams.State;
import org.apache.kafka.streams.state.StreamsMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.yolean.kafka.keyvalue.healthz.StreamsStateListener;
import se.yolean.kafka.keyvalue.healthz.StreamsUncaughtExceptionHandler;
import se.yolean.kafka.keyvalue.http.CacheServer;
import se.yolean.kafka.keyvalue.http.ConfigureRest;
import se.yolean.kafka.keyvalue.http.ReadinessServlet;
import se.yolean.kafka.keyvalue.metrics.StreamsMetrics;

public class App {

  private static final Logger logger = LoggerFactory.getLogger(App.class);

  private long streamsStatusCheckInterval = 5000;
  private int checksBeforeRequireRunning = 3;

  private final StreamsStateListener stateListener = new StreamsStateListener();
  private final StreamsUncaughtExceptionHandler streamsExceptionHandler = new StreamsUncaughtExceptionHandler();
  private long streamsStartTime = -1;
  private StreamsMetrics metrics = null;

  public App(CacheServiceOptions options) {
    logger.info("Starting App using options {}", options);

    KeyvalueUpdate keyvalueUpdate = new KeyvalueUpdateProcessor(
        options.getTopicName(),
        options.getOnUpdate());
    logger.info("Processor created");

    Topology topology = keyvalueUpdate.getTopology();
    logger.info("Topology created, starting Streams using {} custom props",
        options.getStreamsProperties().size());

    KafkaStreams streams = new KafkaStreams(topology, options.getStreamsProperties());
    logger.info("Streams application configured", streams);

    streams.setStateListener(stateListener);
    logger.info("Registered streams state listener {}", stateListener);

    streams.setUncaughtExceptionHandler(streamsExceptionHandler);
    logger.info("Registered streams exception handler {}", streamsExceptionHandler);

    metrics = new StreamsMetrics(streams.metrics());
    logger.info("Will follow metrics through {}", metrics);

    Endpoints endpoints = new Endpoints(keyvalueUpdate);
    logger.info("Starting REST service with endpoints {}", endpoints);

    CacheServer server = new ConfigureRest()
        .createContext(options.getPort(), "/")
        .registerResourceClass(org.glassfish.jersey.jackson.JacksonFeature.class)
        .registerResourceInstance(endpoints)
        .asServlet()
        // TODO metrics: .addCustomServlet(servlet, pathSpec)
        .addCustomServlet(new ReadinessServlet(keyvalueUpdate), "/ready")
        .create();
    logger.info("REST server created {}", server);

    server.start();
    logger.info("REST server stated");

    Runtime.getRuntime().addShutdownHook(new Thread(() -> streams.close()));
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        try {
            server.stop();
        } catch (Exception e) {}
        logger.info("REST server stopped");
    }));

    runStreams(streams);
  }

  private void runStreams(KafkaStreams streams) {
    startStreams(streams);

    while (true) {
      try {
        Thread.sleep(streamsStatusCheckInterval);
      } catch (InterruptedException e) {
        logger.error("Interrupted after streams start", e);
      }
      watchStreams(streams);
    }
  }

  private void watchStreams(KafkaStreams streams) {
    metrics.check();
    if (checksBeforeRequireRunning > 0) {
      logger.info("Looking for signs of successful startup");
      if (metrics.hasSeenAssignedParititions() && stateListener.streamsHasBeenRunning()) {
        checksBeforeRequireRunning = -1;
      } else {
        checksBeforeRequireRunning--;
      }
    }
    if (checksBeforeRequireRunning == 0) {
      logger.error("Failed to confirm streams startup in {} secods", (System.currentTimeMillis() - streamsStartTime) / 1000);
      checksBeforeRequireRunning = 3;
      restartStreams(streams);
    }
  }

  private void startStreams(KafkaStreams streams) {
    streamsStartTime = System.currentTimeMillis();
    logger.info("Starting streams application at {}", streamsStartTime);
    streams.start();
    logger.info("Streams application started");
  }

  private void restartStreams(KafkaStreams streams) {
    logger.warn("Forcing streams instance restart");
    streams.close();
    try {
      Thread.sleep(streamsStatusCheckInterval);
    } catch (InterruptedException e) {
      logger.error("Interrupted after streams close", e);
    }
    startStreams(streams);
  }

}
