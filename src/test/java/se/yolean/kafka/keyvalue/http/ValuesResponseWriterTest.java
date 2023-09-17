package se.yolean.kafka.keyvalue.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import se.yolean.kafka.keyvalue.TopicPartitionOffset;

public class ValuesResponseWriterTest {

  @Test
  void testWrite() throws IOException {
    ValuesResponse response = new ValuesResponse(
        List.of("a".getBytes(), "b".getBytes()).iterator(),
        List.of(new TopicPartitionOffset("mytopic", 0, 0L))
      );

    ValuesResponseWriter w = new ValuesResponseWriter();
    w.objectMapper = new ObjectMapper();
    assertTrue(w.isWriteable(response.getClass(), null, null, null));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
    w.writeTo(response, null, null, null, null, headers, out);
    assertEquals("[x-kkv-last-seen-offsets]", "" + headers.keySet());
    assertEquals("[{\"offset\":0,\"partition\":0,\"topic\":\"mytopic\"}]", headers.getFirst("x-kkv-last-seen-offsets"));
    assertEquals("a\nb\n", out.toString());
  }

}
