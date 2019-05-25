package se.yolean.kafka.keyvalue.onupdate;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.HttpHost;
import org.apache.http.client.utils.URIUtils;

public class UpdateTarget {

  private HttpHost host;
  private URI path;

  static URI toURI(String url) throws RuntimeException {
    try {
      return new URI(url);
    } catch (URISyntaxException e) {
      throw new RuntimeException("Failed to parse update url " + url, e);
    }
  }

  public UpdateTarget(String url) {
    host = URIUtils.extractHost(toURI(url));
    path = toURI(url.substring(url.indexOf('/', 8)));
  }

  HttpHost getHttpclientContextHost() {
    return host;
  }

  URI getHttpUriFromHost() {
    return path;
  }

}
