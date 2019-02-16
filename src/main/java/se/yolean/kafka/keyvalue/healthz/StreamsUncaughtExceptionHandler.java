package se.yolean.kafka.keyvalue.healthz;

import java.lang.Thread.UncaughtExceptionHandler;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class StreamsUncaughtExceptionHandler implements UncaughtExceptionHandler {

  private final Logger logger = LogManager.getLogger(StreamsUncaughtExceptionHandler.class);

  @Override
  public void uncaughtException(Thread t, Throwable e) {
    logger.error("Got uncaught streams exception in thread {}", t, e);
  }

}
