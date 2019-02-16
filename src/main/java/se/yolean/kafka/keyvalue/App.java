package se.yolean.kafka.keyvalue;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.Topology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.yolean.kafka.keyvalue.healthz.StreamsStateListener;
import se.yolean.kafka.keyvalue.healthz.StreamsUncaughtExceptionHandler;
import se.yolean.kafka.keyvalue.http.CacheServer;
import se.yolean.kafka.keyvalue.http.ConfigureRest;
import se.yolean.kafka.keyvalue.http.ReadinessServlet;
import se.yolean.kafka.keyvalue.metrics.PrometheusMetricsServlet;
import se.yolean.kafka.keyvalue.metrics.StreamsMetrics;

public class App {

  private static final Logger logger = LoggerFactory.getLogger(App.class);

  private StreamsStateListener stateListener;
  private StreamsUncaughtExceptionHandler streamsExceptionHandler;
  private StreamsMetrics metrics;
  private Runnable shutdown;

  /**
   * Start a streams app with REST server and return control to the caller.
   * @see #doWhateverRegularMaintenance()
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

    server.start();
    logger.info("REST server stated");

    streams.start();
    logger.info("Streams application started");

    shutdown = new ShutdownHook(streams, server);
    Runtime.getRuntime().addShutdownHook(new Thread(shutdown));
  }

  /**
   * To keep a single control thread for now, the caller of {@link #App(CacheServiceOptions)}
   * should invoke this method at sane intervals,
   * to do things like poll metrics or health/readiness stuff.
   */
  public void doWhateverRegularMaintenance() {
    metrics.check();
  }

  /**
   * @return true if streams seems to have been successfully connected to the source topic
   * <em>at any time</em> i.e. will never toggle from true to false
   */
  public boolean hasConnectedToSourceTopic() {
    return metrics.hasSeenAssignedParititions() && stateListener.streamsHasBeenRunning();
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
