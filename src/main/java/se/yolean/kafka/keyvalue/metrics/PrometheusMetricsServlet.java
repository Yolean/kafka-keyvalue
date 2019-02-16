package se.yolean.kafka.keyvalue.metrics;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.prometheus.client.exporter.MetricsServlet;

public class PrometheusMetricsServlet extends MetricsServlet {

  private static final long serialVersionUID = 1L;

  private StreamsMetrics streamsMetrics;

  public PrometheusMetricsServlet(StreamsMetrics streamsMetrics) {
    this.streamsMetrics = streamsMetrics;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    System.out.println("Got request to metrics");
    streamsMetrics.checkOnPrometheusScrape();
    super.doGet(req, resp);
  }

}
