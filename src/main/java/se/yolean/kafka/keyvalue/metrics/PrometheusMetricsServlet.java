package se.yolean.kafka.keyvalue.metrics;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.prometheus.client.exporter.MetricsServlet;

/**
 * {@value #ENDPOINT_PATH}
 */
public class PrometheusMetricsServlet extends MetricsServlet {

  private static final long serialVersionUID = 1L;

  public static final String ENDPOINT_PATH = "/metrics";

  private KafkaMetrics kafkaMetrics;

  public PrometheusMetricsServlet(KafkaMetrics kafkaMetrics) {
    this.kafkaMetrics = kafkaMetrics;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    kafkaMetrics.checkOnPrometheusScrape();
    super.doGet(req, resp);
  }

}
