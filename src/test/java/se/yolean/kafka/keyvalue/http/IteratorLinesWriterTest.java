package se.yolean.kafka.keyvalue.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import javax.ws.rs.WebApplicationException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import groovyjarjarantlr.collections.List;

public class IteratorLinesWriterTest {

  @Test
  public void testIsWriteable() throws WebApplicationException, IOException {
    IteratorLinesWriter writer = new IteratorLinesWriter();
    writer.objectMapper = new ObjectMapper();
    assertTrue(writer.isWriteable(Iterator.class, null, null, null));
    assertFalse(writer.isWriteable(List.class, null, null, null));
    Iterator<?> values = java.util.List.of(
        Map.of("key", "value"),
        "abc".getBytes(),
        "def"
      ).iterator();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    writer.writeTo(values, null, null, null, null, null, out);
    String[] lines = out.toString().split("\n");
    assertEquals("{\"key\":\"value\"}", lines[0]);
    assertEquals("abc", lines[1]);
    assertEquals("def", lines[2]);
  }

}
