package se.yolean.kafka.keyvalue.onupdate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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

  public static final String TARGETS_CONFIG_SEPARATOR_REGEX = ",";

  public static final String TARGETS_CONFIG_KEY = "update_targets";
  @ConfigProperty(name=TARGETS_CONFIG_KEY)
  java.util.Optional<String> targetsConfig;

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
  public void pollEndBlockingUntilTargetsAck() {
    for (UpdatesDispatcher dispatcher : dispatchers) {
      for (String topic : pollState.keySet()) {
        try {
          dispatcher.dispatch(topic, pollState.get(topic));
        } catch (TargetAckFailedException e) {
          logger.error("Ack failed for {} topic", dispatcher, topic, e);
          throw new RuntimeException("No retry strategy for update ack failure", e);
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
    if (!targetsConfig.isPresent()) {
      logger.info("Update is a NOP. Configure '" + TARGETS_CONFIG_KEY + "' to enable updates");
      dispatchers = Collections.emptyList();
      return;
    }
    List<String> conf = getTargetsConfig();
    if (conf.size() == 0) {
      logger.warn(TARGETS_CONFIG_KEY + " was provided but has zero entries");
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
    String conf = targetsConfig.orElse(null);
    if (conf == null) return Collections.emptyList();
    return java.util.Arrays.asList(conf.split(TARGETS_CONFIG_SEPARATOR_REGEX));
  }

  void stopDispatcher(UpdatesDispatcher dispatcher) {
    try {
      dispatcher.close();
    } catch (Exception e) {
      logger.warn("Failed to close dispatcher " + dispatcher, e);
    }
  }

}
