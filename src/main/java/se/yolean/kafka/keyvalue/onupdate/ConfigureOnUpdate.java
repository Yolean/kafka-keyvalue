package se.yolean.kafka.keyvalue.onupdate;

import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.yolean.kafka.keyvalue.OnUpdate;

@ApplicationScoped
public class ConfigureOnUpdate {

  static final Logger logger = LoggerFactory.getLogger(ConfigureOnUpdate.class);

  public static final String TARGETS_CONFIG_KEY = "update_targets";
  @ConfigProperty(name=TARGETS_CONFIG_KEY)
  // Failed to figure out how to use javax.inject.Provider here, hence the "PossiblyDynamic" suffix so we don't design that out
  java.util.Optional<List<String>> targetUrlsConfigPossiblyDynamic;

  @Produces
  OnUpdate configure() {
    if (!targetUrlsConfigPossiblyDynamic.isPresent()) {
      return new OnUpdateNOP("Configure '" + TARGETS_CONFIG_KEY + "' to enable updates");
    }
    throw new UnsupportedOperationException("Something fails with the optional list, and anyway update support isn't implemented");
    //List<String> conf = targetUrlsConfigPossiblyDynamic.orElse(Collections.emptyList());
    //if (conf.size() == 0) {
    //  return new OnUpdateNOP(TARGETS_CONFIG_KEY + " was provided but has zero entries");
    //}
  }

}
