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

}
