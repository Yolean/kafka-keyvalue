package se.yolean.kafka.keyvalue.metrics;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;

public class MetricsPrint {

  private CollectorRegistry registry;

  public MetricsPrint(CollectorRegistry registry) {
    this.registry = registry;
  }

  public MetricsPrint() {
    this(CollectorRegistry.defaultRegistry);
  }

  public void print(OutputStream out, Set<String> names) {
    OutputStreamWriter osw = new OutputStreamWriter(out, Charset.defaultCharset());
    // https://github.com/prometheus/client_java/blob/parent-0.6.0/simpleclient_httpserver/src/main/java/io/prometheus/client/exporter/HTTPServer.java#L59
    try {
      TextFormat.write004(osw, registry.filteredMetricFamilySamples(names));
      osw.append('\n');
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void printAll(OutputStream out) {
    print(out, new HashSet<String>(0));
  }

}
