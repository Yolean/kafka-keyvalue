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

package se.yolean.kafka.keyvalue.onupdate;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import se.yolean.kafka.keyvalue.OnUpdate;
import se.yolean.kafka.keyvalue.UpdateRecord;

@ApplicationScoped
public class OnUpdateForwarder implements OnUpdate {

  static final Logger logger = LoggerFactory.getLogger(OnUpdateForwarder.class);

  @Inject
  DispatcherConfig dispatcherConfig;

  @Inject
  UpdatesDispatcher dispatcher;

  Map<String, UpdatesBodyPerTopic> pollState = new LinkedHashMap<>(1);

  boolean inPoll = false;

  private Counter meterOnupdateDispatch;

  public OnUpdateForwarder(MeterRegistry registry) {
    this.meterOnupdateDispatch = registry.counter("kkv.onupdate.dispatch");
  }

  @Override
  public void pollStart(Iterable<String> topics) {
    if (inPoll) throw new IllegalStateException("pollStart called twice without pollEnd");
    inPoll = true;
    resetPollState();
  }

  @Override
  public void handle(UpdateRecord update) {
    UpdatesHandler handler = getUpdateHandler(update.getTopic());
    handler.handle(update);
  }

  @Override
  public void sendUpdates() throws UpdateSemanticsSuggestHalt {
    if (!inPoll) throw new IllegalStateException("pollEnd called without pollStart");
    inPoll = false;
    if (!hasPollState()) {
      throw new IllegalStateException("Zero handle(UpdateRecord) calls between pollStart and pollEnd");
    }
    pollState.keySet().forEach(topic -> {
      var body = pollState.get(topic);
      meterOnupdateDispatch.increment();
      dispatcher.dispatch(body);
    });
  }

  boolean hasPollState() {
    return pollState.size() > 0;
  }

  void resetPollState() {
    pollState.clear();
  }

  UpdatesHandler getUpdateHandler(String topic) {
    if (!pollState.containsKey(topic)) {
      pollState.put(topic, dispatcherConfig.getUpdatesHandlerForPoll(topic));
    }
    return pollState.get(topic);
  }

}
