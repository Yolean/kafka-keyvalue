package se.yolean.kafka.keyvalue.http;

import javax.servlet.http.HttpServlet;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigureRest {

  public static final Logger logger = LoggerFactory.getLogger(ConfigureRest.class);

  public static final String RESOURCE_SERVLET_PATHSPEC = "/*";

  public Resources createContext(final int port, String contextPath) {
    Server jettyServer = new Server(port);

    final ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
    context.setContextPath(contextPath);

    jettyServer.setHandler(context);

    logger.debug("Configuring contextPath {}", contextPath);

    final ResourceConfig resourceConfig = new ResourceConfig();

    final Servlets servlets = new Servlets() {

      @Override
      public Servlets addCustomServlet(HttpServlet servlet, String pathSpec) {
        ServletHolder holder = new ServletHolder(servlet);
        context.addServlet(holder, pathSpec);
        return this;
      }

      @Override
      public CacheServer create() {
        return new JettyCacheServer(jettyServer);
      }

    };

    final Resources resources = new Resources() {

      @Override
      public Resources registerResourceClass(Class<?> componentClass) {
        resourceConfig.register(componentClass);
        return this;
      }

      @Override
      public Resources registerResourceInstance(RestResource component) {
        resourceConfig.register(component);
        logger.debug("Registered resource component {}", component);
        return this;
      }

      @Override
      public Servlets asServlet() {
        ServletContainer sc = new ServletContainer(resourceConfig);
        ServletHolder holder = new ServletHolder(sc);
        context.addServlet(holder, RESOURCE_SERVLET_PATHSPEC);
        logger.debug("Added REST servlet with pathSpec {}", RESOURCE_SERVLET_PATHSPEC);
        return servlets;
      }

    };
    return resources;
  }

  public interface Resources {
    public Resources registerResourceClass(Class<?> componentClass);
    public Resources registerResourceInstance(RestResource component);
    public Servlets asServlet();
  }

  public interface Servlets {
    public Servlets addCustomServlet(HttpServlet servlet, String pathSpec);
    public CacheServer create();
  }

}
