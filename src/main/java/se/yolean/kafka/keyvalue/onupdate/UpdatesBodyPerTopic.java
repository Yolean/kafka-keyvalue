package se.yolean.kafka.keyvalue.onupdate;

import java.io.OutputStream;

public interface UpdatesBodyPerTopic extends UpdatesHandler {

  String getContentType();

  int getContentLength();

  String getContent();

  /**
   * @param out UTF-8 stream
   */
  void getContent(OutputStream out);

}
