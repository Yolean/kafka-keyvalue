package se.yolean.kafka.keyvalue;

import java.util.Collection;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.state.StreamsMetadata;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import se.yolean.kafka.keyvalue.healthz.ReadinessImpl;
import se.yolean.kafka.keyvalue.healthz.StreamsStateListener;
import se.yolean.kafka.keyvalue.healthz.StreamsUncaughtExceptionHandler;
import se.yolean.kafka.keyvalue.http.CacheServer;
import se.yolean.kafka.keyvalue.http.ConfigureRest;
import se.yolean.kafka.keyvalue.http.ReadinessServlet;
import se.yolean.kafka.keyvalue.metrics.PrometheusMetricsServlet;
import se.yolean.kafka.keyvalue.metrics.StreamsMetrics;

public class App {

  private static final Logger logger = LogManager.getLogger(App.class);

  private StreamsStateListener stateListener;
  private StreamsUncaughtExceptionHandler streamsExceptionHandler;
  private StreamsMetrics metrics;

  private Runnable shutdown;
  private ReadinessImpl readiness;

  /**
   * Start a streams app with REST server and return control to the caller.
   * @see #getReadiness()
   * @param options well, options
   */
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

    stateListener = new StreamsStateListener();
    streams.setStateListener(stateListener);
    logger.info("Registered streams state listener {}", stateListener);

    streamsExceptionHandler = new StreamsUncaughtExceptionHandler();
    streams.setUncaughtExceptionHandler(streamsExceptionHandler);
    logger.info("Registered streams exception handler {}", streamsExceptionHandler);

    metrics = new StreamsMetrics(streams.metrics());
    logger.info("Will follow metrics through {}", metrics);

    // TODO this is a point-in-time view so to provide a metric of the number of instances we must poll
    Collection<StreamsMetadata> allMetadata = streams.allMetadata();
    if (allMetadata.size() > 1) {
      throw new IllegalStateException("Currently we don't support multiple instances. Might lead to state being a subset. Metadata: " + allMetadata);
    }

    Endpoints endpoints = new Endpoints(keyvalueUpdate);
    logger.info("Starting REST service with endpoints {}", endpoints);

    PrometheusMetricsServlet metricsServlet = new PrometheusMetricsServlet(metrics);

    CacheServer server = new ConfigureRest()
        .createContext(options.getPort(), "/")
        .registerResourceClass(org.glassfish.jersey.jackson.JacksonFeature.class)
        .registerResourceInstance(endpoints)
        .asServlet()
        .addCustomServlet(metricsServlet, "/metrics")
        .addCustomServlet(new ReadinessServlet(keyvalueUpdate), "/ready")
        .create();
    logger.info("REST server created {}", server);

    streams.start();
    logger.info("Streams application started");

    shutdown = new ShutdownHook(streams, server);
    Runtime.getRuntime().addShutdownHook(new Thread(shutdown));

    readiness = new ReadinessImpl(keyvalueUpdate, stateListener, metrics)
        .setHttpEnable(() -> server.start())
        .setHttpDisable(() -> {
          // not reusing code with shutdown hook because we might want to make this a partial shutdown
          try {
            server.stop();
          } catch (Exception e) {
            logger.error("REST server shutdown failed", e);
          }
        });
  }

  public Readiness getReadiness() {
    return readiness;
  }

  public void shutdown() {
    this.shutdown.run();
  }

  private static class ShutdownHook implements Runnable {

    private KafkaStreams streams;
    private CacheServer server;

    ShutdownHook(KafkaStreams streams, CacheServer server) {
      this.streams = streams;
      this.server = server;
    }

    @Override
    public void run() {
      try {
        server.stop();
      } catch (Exception e) {
        logger.error("REST server shutdown failed", e);
      }
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        logger.warn("Interrupted when waiting for server to shut down");
      }
      logger.info("REST server stopped");
      streams.close();
      logger.info("Streams stopped");
    }

  }

}
