package se.yolean.kafka.keyvalue.http;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import com.fasterxml.jackson.databind.ObjectMapper;

import se.yolean.kafka.keyvalue.onupdate.UpdatesBodyPerTopic;

@Provider
public class ValuesResponseWriter implements MessageBodyWriter<ValuesResponse> {

  @Inject
  ObjectMapper objectMapper;

  @Override
  public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return type == ValuesResponse.class;
  }

  @Override
  public void writeTo(ValuesResponse v, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
      MultivaluedMap<String, Object> httpHeaders, OutputStream out)
      throws IOException, WebApplicationException {
    String offsetsJson = objectMapper.writeValueAsString(v.currentOffsets);
    httpHeaders.add(UpdatesBodyPerTopic.HEADER_PREFIX + "last-seen-offsets", offsetsJson);
    while (v.values.hasNext()) {
      out.write(v.values.next());
      out.write('\n');
    }
  }

}
