package se.yolean.kafka.keyvalue.onupdate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.yolean.kafka.keyvalue.OnUpdate;
import se.yolean.kafka.keyvalue.UpdateRecord;
import se.yolean.kafka.keyvalue.onupdate.hc.UpdatesDispatcherHttp;

@ApplicationScoped
public class OnUpdateForwarder implements OnUpdate {

  static final Logger logger = LoggerFactory.getLogger(OnUpdateForwarder.class);

  public static final String TARGETS_CONFIG_KEY = "update_targets";
  @ConfigProperty(name=TARGETS_CONFIG_KEY)
  java.util.Optional<List<String>> targetsConfig;

  List<UpdatesDispatcher> dispatchers = null;

  Map<String, UpdatesBodyPerTopic> state = new LinkedHashMap<>(1);

  UpdatesBodyPerTopic selectUpdatesHandlerImpl(String topic) {
    return new UpdatesBodyPerTopicJSON(topic);
  }

  UpdatesDispatcher selectDispatcherImpl(String configuredTarget) {
    return new UpdatesDispatcherHttp(configuredTarget);
  }

  @Override
  public void pollStart(Iterable<String> topics) {
    resetBodies();
  }

  @Override
  public void handle(UpdateRecord update) {
    UpdatesHandler handler = getUpdateHandler(update.getTopic());
    handler.handle(update);
  }

  @Override
  public void pollEndBlockingUntilTargetsAck() {

  }

  void resetBodies() {
    state.clear();
  }

  UpdatesHandler getUpdateHandler(String topic) {
    if (!state.containsKey(topic)) {
      state.put(topic, selectUpdatesHandlerImpl(topic));
    }
    return state.get(topic);
  }

  Iterable<UpdatesDispatcher> getDispatchers() {
    if (dispatchers == null) {
      updateDispatchersFromConfig();
    }
    return dispatchers;
  }

  void updateDispatchersFromConfig() {
    if (!targetsConfig.isPresent()) {
      logger.info("Update is a NOP. Configure '" + TARGETS_CONFIG_KEY + "' to enable updates");
      dispatchers = Collections.emptyList();
      return;
    }
    List<String> conf = targetsConfig.orElse(Collections.emptyList());
    if (conf.size() == 0) {
      logger.warn(TARGETS_CONFIG_KEY + " was provided but has zero entries");
      dispatchers = Collections.emptyList();
      return;
    }
    dispatchers = new LinkedList<UpdatesDispatcher>();
    for (String target : conf) {
      UpdatesDispatcher dispatcher = selectDispatcherImpl(target);
      dispatchers.add(dispatcher);
      logger.info("Target {} gets dispatcher type {} which calls itself: {}", target, dispatcher.getClass(), dispatcher);
    }
    logger.info("The list of {} update targets is ready", dispatchers);
  }

}
