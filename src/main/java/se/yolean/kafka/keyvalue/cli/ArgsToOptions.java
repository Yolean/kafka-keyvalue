package se.yolean.kafka.keyvalue.cli;

import static net.sourceforge.argparse4j.impl.Arguments.store;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.streams.StreamsConfig;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import se.yolean.kafka.keyvalue.CacheServiceOptions;
import se.yolean.kafka.keyvalue.OnUpdate;
import se.yolean.kafka.keyvalue.onupdate.OnUpdateWithExternalPollTrigger;

public class ArgsToOptions implements CacheServiceOptions {

  /**
   * {@value #DEFAULT_ONUPDATE_TIMEOUT}
   */
  public static final int DEFAULT_ONUPDATE_TIMEOUT = 5000;

  /**
   * {@value #DEFAULT_ONUPDATE_RETRIES}
   */
  public static final int DEFAULT_ONUPDATE_RETRIES = 0;

  private String topicName = null;
  private Integer port = null;
  private String applicationId;
  private Properties streamsProperties = null;
  private Integer startTimeoutSeconds = null;

  private OnUpdateWithExternalPollTrigger onupdate;

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
                "These configs take precedence over those passed via --streams.config. " +
                "The consumer can be configured using prefix: " + StreamsConfig.CONSUMER_PREFIX + ".");

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
        .nargs("+")
        .action(store())
        .required(false)
        .type(String.class)
        .metavar("ONUPDATE")
        .help("A URL to POST the key to upon updates (may be debounced)");

    parser.addArgument("--onupdate-timeout")
        .action(store())
        .required(false)
        .type(Integer.class)
        .metavar("ONUPDATE_TIMEOUT")
        .setDefault(DEFAULT_ONUPDATE_TIMEOUT)
        .dest("onupdateTimeout")
        .help("Milliseconds timeout for onupdate requests");

    parser.addArgument("--onupdate-retries")
        .action(store())
        .required(false)
        .type(Integer.class)
        .metavar("ONUPDATE_RETRIES")
        .setDefault(DEFAULT_ONUPDATE_RETRIES)
        .dest("onupdateRetries")
        .help("The number of retries per onupdate target per update before failure is signalled to the processor."
            + " Set to 0 for 1 try. TODO Default is 2, i.e. 3 tries.");

    parser.addArgument("--starttimeout")
        .action(store())
        .required(false)
        .type(Integer.class)
        .metavar("STARTTIMEOUT")
        .setDefault(0)
        .help("Activates retries: close+restart of the streams setup if it fails to go online."
            + " Useful because Streams' kafka client has retries but failure conditions like missing source topic don't."
            + " Set to >0 to enable a check after this many seconds.");

    return parser;
  }

  public ArgsToOptions(String[] args) {

    @SuppressWarnings("unused") // kept for forward compatibility
    String hostName = null;
    Properties props = new Properties();

    List<String> onupdateUrls = null;
    Integer onupdateTimeout = null;
    Integer onupdateRetries = null;

    ArgumentParser parser = getParser();

    try {
      Namespace res = parser.parseArgs(args);

      topicName = res.getString("topic");
      hostName = res.getString("hostname");
      port = res.getInt("port");
      applicationId = res.getString("applicationId");
      List<String> streamsProps = res.getList("streamsConfig");
      String streamsConfig = res.getString("streamsConfigFile");
      onupdateUrls = res.getList("onupdate");
      onupdateTimeout = res.getInt("onupdateTimeout");
      onupdateRetries = res.getInt("onupdateRetries");

      startTimeoutSeconds = res.getInt("starttimeout");

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

      //props.put(StreamsConfig.CONSUMER_PREFIX + ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

      props.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
      // For when we start to deal with metadata and replicas like in https://medium.com/bakdata/queryable-kafka-topics-with-kafka-streams-8d2cca9de33f
      //props.put(StreamsConfig.APPLICATION_SERVER_CONFIG, hostName + ":" + port);

    } catch (ArgumentParserException e) {
      if (args.length == 0) {
        parser.printHelp();
        System.exit(0);
      }
      parser.handleError(e);
      System.exit(1);
    } catch (IOException e) {
      throw new RuntimeException("Options failed", e);
    }

    this.streamsProperties = props;

    this.onupdate = newOnUpdate(onupdateUrls, onupdateTimeout, onupdateRetries);
  }

  protected OnUpdateWithExternalPollTrigger newOnUpdate(List<String> onupdateUrls, Integer onupdateTimeout,
      Integer onupdateRetries) {
    return new OnUpdateWithExternalPollTrigger(onupdateUrls, onupdateTimeout, onupdateRetries);
  }

  @Override
  public String getTopicName() {
    return topicName;
  }

  @Override
  public Integer getPort() {
    return port;
  }

  @Override
  public Properties getStreamsProperties() {
    return streamsProperties;
  }

  @Override
  public String getApplicationId() {
    return applicationId;
  }

  @Override
  public Integer getStartTimeoutSecods() {
    return startTimeoutSeconds;
  }

  @Override
  public OnUpdate getOnUpdate() {
    return this.onupdate;
  }

  OnUpdateWithExternalPollTrigger getOnUpdateImpl() {
    return this.onupdate;
  }

}
