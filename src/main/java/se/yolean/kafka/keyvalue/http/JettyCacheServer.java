package se.yolean.kafka.keyvalue.http;

import org.eclipse.jetty.server.Server;

public class JettyCacheServer implements CacheServer {

  private Server jettyServer;

  JettyCacheServer(Server jettyServer) {
    this.jettyServer = jettyServer;
  }

  @Override
  public void start() {
    try {
      jettyServer.start();
    } catch (Exception e) {
      throw new RuntimeException("Failed to start Jetty server", e);
    }
  }

  @Override
  public void stop() throws Exception {
    if (jettyServer != null) {
      jettyServer.stop();
    }
  }

}
