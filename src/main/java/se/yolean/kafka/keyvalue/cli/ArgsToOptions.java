package se.yolean.kafka.keyvalue.cli;

import static net.sourceforge.argparse4j.impl.Arguments.store;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import org.apache.kafka.streams.StreamsConfig;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import se.yolean.kafka.keyvalue.CacheServiceOptions;
import se.yolean.kafka.keyvalue.OnUpdate;
import se.yolean.kafka.keyvalue.onupdate.OnUpdateFactory;

public class ArgsToOptions implements CacheServiceOptions {

  private OnUpdateFactory onUpdateFactory = null;

  private String topicName = null;
  private Integer port = null;
  private String applicationId;
  private Properties streamsProperties = null;
  private OnUpdate onUpdate = null;

  public ArgsToOptions setOnUpdateFactory(OnUpdateFactory factory) {
    this.onUpdateFactory  = factory;
    return this;
  }

  private ArgumentParser getParser() {
    ArgumentParser parser = ArgumentParsers
        .newFor("kafka-keyvalue").build()
        .defaultHelp(true)
        .description("This Kafka Streams application is used to interactively query values from Kafka topics");

    parser.addArgument("--topic")
        .action(store())
        .required(true)
        .type(String.class)
        .metavar("TOPIC")
        .help("process messages from this topic");

    parser.addArgument("--streams-props")
        .nargs("+")
        .required(false)
        .metavar("PROP-NAME=PROP-VALUE")
        .type(String.class)
        .dest("streamsConfig")
        .help("kafka streams related configuration properties like bootstrap.servers etc. " +
                "These configs take precedence over those passed via --streams.config.");

    parser.addArgument("--streams.config")
        .action(store())
        .required(false)
        .type(String.class)
        .metavar("CONFIG-FILE")
        .dest("streamsConfigFile")
        .help("streams config properties file.");

    parser.addArgument("--application-id")
        .action(store())
        .required(false)
        .type(String.class)
        .metavar("APPLICATION-ID")
        .dest("applicationId")
        .setDefault("streams-processor-default-application-id")
        .help("The id of the streams application to use. Useful for monitoring and resetting the streams application state.");

    parser.addArgument("--hostname")
        .action(store())
        .required(false)
        .type(String.class)
        .metavar("HOSTNAME")
        .setDefault("localhost")
        .help("Not used at the moment, kept for CLI compatibility with https://github.com/bakdata/kafka-key-value-store");

    parser.addArgument("--port")
        .action(store())
        .required(false)
        .type(Integer.class)
        .metavar("PORT")
        .setDefault(8080)
        .help("The TCP Port for the HTTP REST Service");

    parser.addArgument("--onupdate")
        .action(store())
        .required(false)
        .type(String.class)
        .metavar("ONUPDATE")
        .help("A URL to POST the key to upon updates (may be debounced)");

    return parser;
  }

  public CacheServiceOptions fromCommandLineArguments(String[] args) {

    @SuppressWarnings("unused") // kept for forward compatibility
    String hostName = null;
    String onupdateUrl = null;
    Properties props = new Properties();

    ArgumentParser parser = getParser();

    try {
      Namespace res = parser.parseArgs(args);

      topicName = res.getString("topic");
      hostName = res.getString("hostname");
      port = res.getInt("port");
      applicationId = res.getString("applicationId");
      List<String> streamsProps = res.getList("streamsConfig");
      String streamsConfig = res.getString("streamsConfigFile");
      onupdateUrl = res.getString("onupdate");

      if (streamsProps == null && streamsConfig == null) {
        throw new ArgumentParserException("Either --streams-props or --streams.config must be specified.", parser);
      }

      if (streamsConfig != null) {
        try (InputStream propStream = Files.newInputStream(Paths.get(streamsConfig))) {
          props.load(propStream);
        }
      }

      if (streamsProps != null) {
        for (String prop : streamsProps) {
          String[] pieces = prop.split("=");
          if (pieces.length != 2)
            throw new IllegalArgumentException("Invalid property: " + prop);
          props.put(pieces[0], pieces[1]);
        }
      }

      props.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
      // props.put(StreamsConfig.APPLICATION_SERVER_CONFIG, hostName + ":" + port);
    } catch (ArgumentParserException e) {
      if (args.length == 0) {
        parser.printHelp();
        System.exit(0);
      } else {
        parser.handleError(e);
        System.exit(1);
      }
    } catch (IOException e) {
      throw new RuntimeException("Options failed", e);
    }

    this.streamsProperties = props;

    if (onupdateUrl != null) {
      if (this.onUpdateFactory == null) {
        throw new IllegalStateException("setOnUpdateFactory must be called first");
      }
      this.onUpdate = this.onUpdateFactory.fromUrl(onupdateUrl);
    }

    return this;
  }

  /* (non-Javadoc)
   * @see se.yolean.kafka.keyvalue.CacheServiceOptions#getTopicName()
   */
  @Override
  public String getTopicName() {
    return topicName;
  }

  /* (non-Javadoc)
   * @see se.yolean.kafka.keyvalue.CacheServiceOptions#getPort()
   */
  @Override
  public Integer getPort() {
    return port;
  }

  /* (non-Javadoc)
   * @see se.yolean.kafka.keyvalue.CacheServiceOptions#getStreamsProperties()
   */
  @Override
  public Properties getStreamsProperties() {
    return streamsProperties;
  }

  /* (non-Javadoc)
   * @see se.yolean.kafka.keyvalue.CacheServiceOptions#getOnUpdate()
   */
  @Override
  public OnUpdate getOnUpdate() {
    return onUpdate;
  }

  /* (non-Javadoc)
   * @see se.yolean.kafka.keyvalue.CacheServiceOptions#getApplicationId()
   */
  @Override
  public String getApplicationId() {
    return applicationId;
  }

}
