// Copyright 2019 Yolean AB
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package se.yolean.kafka.keyvalue.onupdate.hc;

import java.io.IOException;
import java.net.URI;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.ServiceUnavailableRetryStrategy;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
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

  public UpdatesDispatcherHttp(String configuredTarget, RetryDecisions retryDecisions) {
    target = new UpdateTarget(configuredTarget);
    HttpHost host = target.getHttpclientContextHost(); // If we want to manage contexts
    logger.info("Creating http client for host {} target {}", host, target);

    // Disable ssl for now, as we only target sidecars and native images get org.apache.http.ssl.SSLInitializationException: TLS SSLContext not available
    Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
        .register("http", PlainConnectionSocketFactory.getSocketFactory())
        //.register("https", SSLConnectionSocketFactory.getSocketFactory())
        .build();
    BasicHttpClientConnectionManager connectionManager = new BasicHttpClientConnectionManager(registry, null, null, null);


    HttpRequestRetryHandler retryHandler = new RetryConnectionRefusedThreadSleep(retryDecisions);
    ServiceUnavailableRetryStrategy serviceUnavailStrategy = new RetryServiceUnavailableBackoff(retryDecisions);

    client = HttpClients.custom()
        .setConnectionManager(connectionManager)
        .setRetryHandler(retryHandler)
        .setServiceUnavailableRetryStrategy(serviceUnavailStrategy)
        .build();
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
