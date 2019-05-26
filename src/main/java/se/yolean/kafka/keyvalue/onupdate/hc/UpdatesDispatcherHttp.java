package se.yolean.kafka.keyvalue.onupdate.hc;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.StringEntity;
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
  HttpClientContext context;

  public UpdatesDispatcherHttp(String configuredTarget) {
    target = new UpdateTarget(configuredTarget);
    HttpHost host = target.getHttpclientContextHost(); // If we want to manage contexts
    logger.info("Creating http client for host {} target {}", host, target);

    context = HttpClientContext.create();
    context.setTargetHost(host);

    BasicHttpClientConnectionManager connectionManager = new BasicHttpClientConnectionManager();
    client = HttpClients.createMinimal(connectionManager);
  }

  @Override
  public void dispatch(String topicName, UpdatesBodyPerTopic body) throws TargetAckFailedException {
    HttpPost post = new HttpPost(target.getHttpUriFromHost(topicName));
    post.addHeader(UpdatesBodyPerTopic.HEADER_TOPIC, topicName); // TODO body should declare all headers instead
    post.setEntity(getEntity(body));
    ResponseResult result;
    try {
      result = client.execute(post, responseHandler, context);
    } catch (ClientProtocolException e) {
      throw new TargetAckFailedException(e);
    } catch (IOException e) {
      throw new TargetAckFailedException(e);
    }
    if (!result.isAck()) {
      throw new TargetAckFailedException(result.getStatus());
    }
  }

  private HttpEntity getEntity(UpdatesBodyPerTopic body) {
    StringEntity entity;
    try {
      entity = new StringEntity(body.getContent());
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
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

}
