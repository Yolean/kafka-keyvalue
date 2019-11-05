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

  @Override
  public String toString() {
    return host.toString() + path.toString();
  }

}
