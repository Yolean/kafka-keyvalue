package se.yolean.kafka.keyvalue.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.yolean.kafka.keyvalue.App;
import se.yolean.kafka.keyvalue.CacheServiceOptions;
import se.yolean.kafka.keyvalue.onupdate.OnUpdateFactory;

public class Main {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  private static long maintenanceCallInterval = 5000;

  public static void main(String[] args) {
    OnUpdateFactory onUpdateFactory = OnUpdateFactory.getInstance();

    CacheServiceOptions options = new ArgsToOptions()
        .setOnUpdateFactory(onUpdateFactory)
        .fromCommandLineArguments(args);

    long appStartTime = -1;
    App app = null;

    while (true) {
      if (app == null) {
        appStartTime  = System.currentTimeMillis();
        logger.info("Initializing streams app");
        app = new App(options);
      }

      try {
        Thread.sleep(maintenanceCallInterval);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }

      app.doWhateverRegularMaintenance();

      if (!app.hasConnectedToSourceTopic()
          && options.getStartTimeoutSecods() > 0
          && System.currentTimeMillis() - appStartTime > options.getStartTimeoutSecods() * 1000) {
        logger.error("No sign of success for app start. Shutting down to retry.");
        app.shutdown();
        app = null;
      }
    }
  }

}
