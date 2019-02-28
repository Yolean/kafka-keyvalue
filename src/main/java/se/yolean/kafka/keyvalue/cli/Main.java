package se.yolean.kafka.keyvalue.cli;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import se.yolean.kafka.keyvalue.App;
import se.yolean.kafka.keyvalue.CacheServiceOptions;
import se.yolean.kafka.keyvalue.Readiness;
import se.yolean.kafka.keyvalue.onupdate.OnUpdateWithExternalPollTrigger;
import se.yolean.kafka.keyvalue.onupdate.UnrecognizedOnupdateResult;

public class Main {

  private static final Logger logger = LogManager.getLogger(Main.class);

  private static final int POLL_INTERVAL = 1000;

  private static long prevpollstart = 0;

  public static void main(String[] args) {
    ArgsToOptions options = new ArgsToOptions(args);

    while (!appstart(options)) {
      logger.info("Retrying streams app start");
    }

    OnUpdateWithExternalPollTrigger onupdate = options.getOnUpdateImpl();
    while (true) {
      try {
        pollOnupdate(onupdate);
      } catch (UnrecognizedOnupdateResult e) {
        logger.error("Poll failed", e);
        logger.info("Shutting down because of {}", e);
        // There should be shutdown hooks that handle cleanup
        System.exit(1);
      }
    }
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
        readiness.enableServiceEndpoints();
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
      Thread.sleep(POLL_INTERVAL);
    } catch (InterruptedException e) {
      logger.error("Interrupted when polling for app startup status");
    }
    return readiness.isAppReady();
  }

  /**
   * For now there's only one thing we need to do after readiness: poll for request completion.
   * @throws UnrecognizedOnupdateResult if any onupdate resulted in neither a response nor a recognized error
   */
  private static void pollOnupdate(OnUpdateWithExternalPollTrigger onupdate) throws UnrecognizedOnupdateResult {
    try {
      // try to poll regularly, 1 second
      Thread.sleep(Math.max(POLL_INTERVAL,
          Math.min(1, POLL_INTERVAL - System.currentTimeMillis() + prevpollstart)));
    } catch (InterruptedException e) {
      logger.error("Interrupted when polling for onupdate progress");
    }
    prevpollstart = System.currentTimeMillis();
    onupdate.checkCompletion();
  }

}
