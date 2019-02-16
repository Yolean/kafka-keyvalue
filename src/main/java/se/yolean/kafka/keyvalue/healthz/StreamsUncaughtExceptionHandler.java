package se.yolean.kafka.keyvalue.healthz;

import java.lang.Thread.UncaughtExceptionHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamsUncaughtExceptionHandler implements UncaughtExceptionHandler {

  private final Logger logger = LoggerFactory.getLogger(StreamsUncaughtExceptionHandler.class);

  @Override
  public void uncaughtException(Thread t, Throwable e) {
    logger.error("Got uncaught streams exception in thread {}", t, e);
  }

}
