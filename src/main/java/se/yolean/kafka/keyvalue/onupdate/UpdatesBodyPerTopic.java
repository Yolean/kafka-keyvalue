package se.yolean.kafka.keyvalue.onupdate;

import java.io.OutputStream;

import se.yolean.kafka.keyvalue.UpdateRecord;

public interface UpdatesBodyPerTopic {

  String getContentType();

  void handle(UpdateRecord update);

  int getContentLength();

  String getContent();

  /**
   * @param out UTF-8 stream
   */
  void getContent(OutputStream out);

}
