package se.yolean.kafka.keyvalue.config;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.microprofile.config.spi.Converter;

/**
 * Understands a subset of https://golang.org/pkg/time/#ParseDuration syntax.
 *
 * Regex: {@value #REGEX}
 */
public class DurationConverter implements Converter<Duration> {

  public static final String REGEX = "^(\\d+)(m|s|ms)$";

  public static final Pattern pattern = Pattern.compile(REGEX);

  @Override
  public Duration convert(String value) {
    Matcher matcher = pattern.matcher(value);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Failed to parse duration value " + value);
    }
    int n = Integer.parseInt(matcher.group(1));
    String unit = matcher.group(2);
    if ("m".equals(unit)) return Duration.ofMinutes(n);
    if ("s".equals(unit)) return Duration.ofSeconds(n);
    if ("ms".equals(unit)) return Duration.ofMillis(n);
    throw new IllegalArgumentException("Unexpected time unit '" + unit + "' in duration value " + value);
  }

}
