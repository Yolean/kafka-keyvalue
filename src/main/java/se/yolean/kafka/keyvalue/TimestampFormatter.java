package se.yolean.kafka.keyvalue;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * Just to make sure we get UTC.
 */
public class TimestampFormatter extends SimpleDateFormat {

  private static final String DEFAULT_FORMAT = "yyyyMMdd't'HHmmss";
  private static final long serialVersionUID = 1L;

  /**
   * Sets a short human-readable timestamp useful for logging, application id etc.
   */
  public TimestampFormatter() {
    this(DEFAULT_FORMAT);
  }

  public TimestampFormatter(String format) {
    super(format);
    setTimeZone(TimeZone.getTimeZone("UTC"));
  }

}
