package se.yolean.kafka.keyvalue.http;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Iterator;

import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;

import com.fasterxml.jackson.databind.ObjectMapper;

@Provider
public class IteratorLinesWriter implements MessageBodyWriter<Iterator<?>> {

  @Inject
  ObjectMapper objectMapper;

  @Override
  public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return Iterator.class.isAssignableFrom(type);
  }

  @Override
  public void writeTo(Iterator<?> values, Class<?> type, Type genericType, Annotation[] annotations,
      MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
      throws IOException, WebApplicationException {
    while (values.hasNext()) {
      Object value = values.next();
      if (value instanceof byte[]) {
        entityStream.write((byte[]) value);
      } else if (value instanceof String) {
        entityStream.write(((String) value).getBytes());
      } else {
        entityStream.write(objectMapper.writeValueAsBytes(value));
      }
      entityStream.write('\n');
    }
  }

}
