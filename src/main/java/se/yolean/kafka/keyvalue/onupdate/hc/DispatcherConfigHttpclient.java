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

import javax.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import se.yolean.kafka.keyvalue.onupdate.DispatcherConfig;
import se.yolean.kafka.keyvalue.onupdate.UpdatesBodyPerTopic;
import se.yolean.kafka.keyvalue.onupdate.UpdatesBodyPerTopicJSON;
import se.yolean.kafka.keyvalue.onupdate.UpdatesDispatcher;

@Singleton
public class DispatcherConfigHttpclient implements DispatcherConfig, RetryDecisions {

  //@ConfigProperty(name="update_connection_timeout", defaultValue="1")
  //Duration connectionTimeout;

  @ConfigProperty(name="max_retries_connection_refused", defaultValue="8")
  int maxRetriesConnectionRefused;

  @ConfigProperty(name="max_retries_status", defaultValue="8")
  int maxRetriesStatus;

  @Override
  public UpdatesBodyPerTopic getUpdatesHandlerForPoll(String topic) {
    return new UpdatesBodyPerTopicJSON(topic);
  }

  @Override
  public UpdatesDispatcher getDispatcher(String configuredTarget) {
    UpdatesDispatcherHttp http = new UpdatesDispatcherHttp(
        configuredTarget,
        (RetryDecisions) this);
    return http;
  }

  @Override
  //@Counted(name="retries_connection_refused", description="Retries granted for errors that look like connection refused")
  public boolean onConnectionRefused(int count) {
    return count <= maxRetriesConnectionRefused;
  }

  @Override
  //@Counted(name="retries_status", description="Retries granted for unexpected http statuses")
  public boolean onStatus(int count, int status) {
    if (ResponseResult.isAck(status)) return false;
    return count <= maxRetriesStatus;
  }

}
