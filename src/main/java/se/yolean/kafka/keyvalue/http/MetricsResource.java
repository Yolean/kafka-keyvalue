package se.yolean.kafka.keyvalue.http;

import java.io.IOException;
import java.io.OutputStream;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;

import se.yolean.kafka.keyvalue.ConsumerAtLeastOnce;
import se.yolean.kafka.keyvalue.metrics.MetricsPrint;

@Path("/metrics")
public class MetricsResource {

  @Inject
  ConsumerAtLeastOnce consumer;

  final MetricsPrint metricsPrint = new MetricsPrint();

  @GET
  public StreamingOutput prometheus() {
    consumer.getMetrics().check();
    return new StreamingOutput() {
      @Override
      public void write(OutputStream output) throws IOException, WebApplicationException {
        metricsPrint.printAll(output);
      }
    };
  }

}
