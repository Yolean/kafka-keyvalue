package se.yolean.kafka.keyvalue.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.yolean.kafka.keyvalue.App;
import se.yolean.kafka.keyvalue.CacheServiceOptions;
import se.yolean.kafka.keyvalue.Readiness;
import se.yolean.kafka.keyvalue.onupdate.OnUpdateFactory;

public class Main {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);


  public static void main(String[] args) {
    OnUpdateFactory onUpdateFactory = OnUpdateFactory.getInstance();

    CacheServiceOptions options = new ArgsToOptions()
        .setOnUpdateFactory(onUpdateFactory)
        .fromCommandLineArguments(args);

    while (!appstart(options)) logger.info("Retrying streams app start");
  }

  /**
   * @return false to indicate that startup failed
   */
  private static boolean appstart(CacheServiceOptions options) {

    long appStartTime  = System.currentTimeMillis();
    App app = new App(options);

    Readiness readiness = app.getReadiness();

    while (true) {
      if (poll(readiness)) {
        logger.info("App looks ready. Asking for HTTP server to be enabled.");
        readiness.httpEnable();
        return true;
      }
      if (options.getStartTimeoutSecods() > 0
          && System.currentTimeMillis() - appStartTime > options.getStartTimeoutSecods() * 1000) {
        logger.error("No sign of success for app start. Shutting down to retry.");
        app.shutdown();
        return false;
      }
    }
  }

  private static boolean poll(Readiness readiness) {
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      logger.error("Interrupted when polling for app startup status");
    }
    return readiness.isAppReady();
  }

}
