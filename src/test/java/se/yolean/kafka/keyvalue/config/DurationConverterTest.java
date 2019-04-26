package se.yolean.kafka.keyvalue.config;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

import org.eclipse.microprofile.config.spi.Converter;
import org.junit.jupiter.api.Test;

class DurationConverterTest {

  @Test
  void testConvertS() {
    Converter<Duration> converter = new DurationConverter();
    assertEquals(30, converter.convert("30s").getSeconds());
    assertEquals(5, converter.convert("5s").getSeconds());
  }

  @Test
  void testConvertMs() {
    Converter<Duration> converter = new DurationConverter();
    assertEquals(30000000, converter.convert("30ms").getNano());
    assertEquals(5000000, converter.convert("5ms").getNano());
  }

  /**
   * Needed because with Quarkus 0.14.0 mvn runs would crash with Failed to parse duration value PT1S even when no value was set
   */
  @Test
  void testConvertISO() {
    Converter<Duration> converter = new DurationConverter();
    assertEquals(1, converter.convert("PT1S").getSeconds());
  }

}
