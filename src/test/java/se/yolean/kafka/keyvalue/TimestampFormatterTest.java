package se.yolean.kafka.keyvalue;

import static org.junit.jupiter.api.Assertions.*;

import java.text.DateFormat;

import org.junit.jupiter.api.Test;

class TimestampFormatterTest {

  @Test
  void test() {
    DateFormat formatter = new TimestampFormatter();
    assertEquals("20190309t122836", formatter.format(1552134516000L));
  }

}
