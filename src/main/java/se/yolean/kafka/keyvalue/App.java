package se.yolean.kafka.keyvalue;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.Topology;

import se.yolean.kafka.keyvalue.http.CacheServer;
import se.yolean.kafka.keyvalue.http.ConfigureRest;
import se.yolean.kafka.keyvalue.http.ReadinessServlet;

public class App {

  public App(CacheServiceOptions options) {
    KeyvalueUpdate keyvalueUpdate = new KeyvalueUpdateProcessor(
        options.getTopicName(),
        options.getOnUpdate());
    Topology topology = keyvalueUpdate.getTopology();

    KafkaStreams streams = new KafkaStreams(topology, options.getStreamsProperties());

    Endpoints endpoints = new Endpoints(keyvalueUpdate);
    CacheServer server = new ConfigureRest()
        .createContext(options.getPort(), "/")
        .registerResourceClass(org.glassfish.jersey.jackson.JacksonFeature.class)
        .registerResourceInstance(endpoints)
        .asServlet()
        // TODO metrics: .addCustomServlet(servlet, pathSpec)
        .addCustomServlet(new ReadinessServlet(keyvalueUpdate), "/ready")
        .create();
    server.start();

    Runtime.getRuntime().addShutdownHook(new Thread(() -> streams.close()));
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        try {
            server.stop();
        } catch (Exception e) {}
    }));

    streams.start();
  }

}
