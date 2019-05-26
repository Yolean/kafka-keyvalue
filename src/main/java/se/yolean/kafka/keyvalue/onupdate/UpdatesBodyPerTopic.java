package se.yolean.kafka.keyvalue.onupdate;

import java.io.OutputStream;
import java.util.Map;

public interface UpdatesBodyPerTopic extends UpdatesHandler {

  public static final String HEADER_PREFIX = "x-kkv-";

  public static final String HEADER_TOPIC = HEADER_PREFIX + "topic";

  public static final String HEADER_OFFSETS = HEADER_PREFIX + "offsets";

  public static String formatOffsetsHeader(Iterable<java.util.Map.Entry<Integer, Integer>> partitionoffsets) {
    throw new UnsupportedOperationException("Not implemented");
  }

  Map<String,String> getHeaders();

  String getContentType();

  int getContentLength();

  String getContent();

  /**
   * @param out UTF-8 stream
   */
  void getContent(OutputStream out);

}
