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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import se.yolean.kafka.keyvalue.OnUpdate;
import se.yolean.kafka.keyvalue.UpdateRecord;

@Singleton
public class OnUpdateForwarder implements OnUpdate {

  static final Logger logger = LoggerFactory.getLogger(OnUpdateForwarder.class);

  @ConfigProperty(name="target") Optional<String> target;
  @ConfigProperty(name="target1") Optional<String> target1;
  @ConfigProperty(name="target2") Optional<String> target2;
  @ConfigProperty(name="target3") Optional<String> target3;
  @ConfigProperty(name="target4") Optional<String> target4;
  @ConfigProperty(name="target5") Optional<String> target5;
  @ConfigProperty(name="target6") Optional<String> target6;
  @ConfigProperty(name="target7") Optional<String> target7;
  @ConfigProperty(name="target8") Optional<String> target8;
  @ConfigProperty(name="target9") Optional<String> target9;

  @Inject
  DispatcherConfig dispatcherConfig;

  List<UpdatesDispatcher> dispatchers = null;

  Map<String, UpdatesBodyPerTopic> pollState = new LinkedHashMap<>(1);

  void start(@Observes StartupEvent ev) {
    updateDispatchersFromConfig();
  }

  public void stop(@Observes ShutdownEvent ev) {
    for (UpdatesDispatcher dispatcher : dispatchers) {
      stopDispatcher(dispatcher);
    }
  }

  @Override
  public void pollStart(Iterable<String> topics) {
    resetPollState();
  }

  @Override
  public void handle(UpdateRecord update) {
    UpdatesHandler handler = getUpdateHandler(update.getTopic());
    handler.handle(update);
  }

  @Override
  public void pollEndBlockingUntilTargetsAck() throws UpdateSemanticsSuggestHalt {
    for (UpdatesDispatcher dispatcher : dispatchers) {
      for (String topic : pollState.keySet()) {
        try {
          dispatcher.dispatch(topic, pollState.get(topic));
        } catch (TargetAckFailedException e) {
          logger.error("Ack failed for {} topic {}", dispatcher, topic, e);
          throw new UpdateSemanticsSuggestHalt("Will stop fowarding updates upon any error, to not violate consistency", e);
        }
      }
    }
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

  Iterable<UpdatesDispatcher> getDispatchers() {
    if (dispatchers == null) {
      throw new IllegalStateException("Dispatches should have been initialized");
    }
    return dispatchers;
  }

  void updateDispatchersFromConfig() {
    List<String> conf = getTargetsConfig();

    if (conf.size() == 0) {
      logger.info("Update is a NOP. Configure 'target' or 'targetX' to enable updates");
      dispatchers = Collections.emptyList();
      return;
    }

    dispatchers = new LinkedList<UpdatesDispatcher>();
    for (String target : conf) {
      UpdatesDispatcher dispatcher = dispatcherConfig.getDispatcher(target);
      dispatchers.add(dispatcher);
      logger.info("Target {} gets dispatcher type {} which calls itself: {}", target, dispatcher.getClass(), dispatcher);
    }
    logger.info("The list of {} update targets is ready", dispatchers);
  }

  List<String> getTargetsConfig() {
    return Arrays.asList(
        target.orElse(null),
        target1.orElse(null),
        target2.orElse(null),
        target3.orElse(null),
        target4.orElse(null),
        target5.orElse(null),
        target6.orElse(null),
        target7.orElse(null),
        target8.orElse(null),
        target9.orElse(null))
        .stream()
        .filter(t -> t != null)
        .collect(Collectors.toList());
  }

  void stopDispatcher(UpdatesDispatcher dispatcher) {
    try {
      logger.info("Stopping update dispatcher");
      dispatcher.close();
    } catch (Exception e) {
      logger.warn("Failed to close dispatcher " + dispatcher, e);
    }
  }

}
