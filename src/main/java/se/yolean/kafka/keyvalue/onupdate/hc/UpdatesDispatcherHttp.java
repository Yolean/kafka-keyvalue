package se.yolean.kafka.keyvalue.onupdate.hc;

import java.io.IOException;
import java.net.URI;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.yolean.kafka.keyvalue.onupdate.TargetAckFailedException;
import se.yolean.kafka.keyvalue.onupdate.UpdatesBodyPerTopic;
import se.yolean.kafka.keyvalue.onupdate.UpdatesDispatcher;

public class UpdatesDispatcherHttp implements UpdatesDispatcher {

  static final Logger logger = LoggerFactory.getLogger(UpdatesDispatcherHttp.class);

  ResponseHandlerAck responseHandler = new ResponseHandlerAck();
  UpdateTarget target;
  CloseableHttpClient client;

  public UpdatesDispatcherHttp(String configuredTarget) {
    target = new UpdateTarget(configuredTarget);
    HttpHost host = target.getHttpclientContextHost(); // If we want to manage contexts
    logger.info("Creating http client for host {} target {}", host, target);

    BasicHttpClientConnectionManager connectionManager = new BasicHttpClientConnectionManager();
    client = HttpClients.createMinimal(connectionManager);
  }

  @Override
  public void dispatch(String topicName, UpdatesBodyPerTopic body) throws TargetAckFailedException {
    HttpHost host = target.getHttpclientContextHost();
    URI path = target.getHttpUriFromHost(topicName);
    HttpPost post = new HttpPost(path);
    body.getHeaders().forEach((name, value) -> post.setHeader(name, value));
    post.setEntity(getEntity(body));
    ResponseResult result;
    try {
      result = client.execute(host, post, responseHandler);
    } catch (ClientProtocolException e) {
      throw new TargetAckFailedException(e);
    } catch (IOException e) {
      throw new TargetAckFailedException(e);
    }
    if (!result.isAck()) {
      logger.warn("Non-ack response from {}{}: {}", host, path, result);
      throw new TargetAckFailedException(result.getStatus());
    }
  }

  private HttpEntity getEntity(UpdatesBodyPerTopic body) {
    ByteArrayEntity entity;
    entity = new ByteArrayEntity(body.getContent());
    entity.setContentType(body.getContentType());
    return entity;
  }

  @Override
  public void close() {
    logger.info("Closing http client");
    try {
      client.close();
    } catch (IOException e) {
      logger.error("Failed to close http client");
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + '[' + target + ']';
  }

}
