package se.yolean.kafka.keyvalue.http;

import javax.servlet.http.HttpServlet;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

public class ConfigureRest {

  public Resources createContext(final int port, String contextPath) {
    final ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/");
    final ResourceConfig resourceConfig = new ResourceConfig();
    final Servlets servlets = new Servlets() {

      @Override
      public Servlets addCustomServlet(HttpServlet servlet, String pathSpec) {
        throw new UnsupportedOperationException("Not implemented");
      }

      @Override
      public CacheServer create() {
        return new JettyCacheServer(port);
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
        return this;
      }

      @Override
      public Servlets asServlet() {
        ServletContainer sc = new ServletContainer(resourceConfig);
        ServletHolder holder = new ServletHolder(sc);
        context.addServlet(holder, "/*");
        return servlets;
      }

    };
    return resources;
  }

  public interface Resources {
    public Resources registerResourceClass(Class<?> clazz);
    public Resources registerResourceInstance(RestResource component);
    public Servlets asServlet();
  }

  public interface Servlets {
    public Servlets addCustomServlet(HttpServlet servlet, String pathSpec);
    public CacheServer create();
  }

}
