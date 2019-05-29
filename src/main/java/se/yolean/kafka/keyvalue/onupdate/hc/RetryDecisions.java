package se.yolean.kafka.keyvalue.onupdate.hc;

/**
 * Trying to make sens of apache httpclient's retry options,
 * and interpret to something domain specific.
 */
public interface RetryDecisions {

  boolean onConnectionRefused(int count);

  boolean onStatus(int count, int status);

}
