package se.yolean.kafka.keyvalue.onupdate;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.HttpHost;
import org.apache.http.client.utils.URIUtils;

public class UpdateTarget {

  public static final String TOPIC_NAME_REPLACE_TOKEN = "__TOPIC__";

  final HttpHost host;
  final URI path;
  final boolean replaceToken;

  static URI toURI(String url) throws RuntimeException {
    try {
      return new URI(url);
    } catch (URISyntaxException e) {
      throw new RuntimeException("Failed to parse update url " + url, e);
    }
  }

  static URI replaceToken(URI path, String topicName) {
    try {
      return new URI(path.toString().replace(TOPIC_NAME_REPLACE_TOKEN, topicName));
    } catch (URISyntaxException e) {
      throw new RuntimeException("Failed to insert topic name '" + topicName + "' in URI '" + path);
    }
  }

  public UpdateTarget(String url) {
    host = URIUtils.extractHost(toURI(url));
    path = toURI(url.substring(url.indexOf('/', 8)));
    replaceToken = !path.equals(replaceToken(path, "(topic-name-would-go-here)"));
  }

  HttpHost getHttpclientContextHost() {
    return host;
  }

  URI getHttpUriFromHost(String topicName) {
    if (replaceToken) {
      return replaceToken(path, topicName);
    }
    return path;
  }

}
