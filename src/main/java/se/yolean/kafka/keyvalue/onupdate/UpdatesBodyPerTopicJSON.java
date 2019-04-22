package se.yolean.kafka.keyvalue.onupdate;

import java.io.OutputStream;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import se.yolean.kafka.keyvalue.UpdateRecord;

public class UpdatesBodyPerTopicJSON implements UpdatesBodyPerTopic {

  public static final String CONTENT_TYPE = "application/json";

  public static final String VERSION_KEY = "v";
  public static final String OFFSETS_KEY = "offsets";
  public static final String UPDATES_KEY = "updates";

  private JsonObjectBuilder builder;
  private JsonObjectBuilder offsets;
  private JsonObjectBuilder updates;
  private JsonObjectBuilder json;

  public UpdatesBodyPerTopicJSON() {
    builder = Json.createObjectBuilder();
    offsets = Json.createObjectBuilder();
    updates = Json.createObjectBuilder();
    json = builder.add(VERSION_KEY, 1);
  }

  JsonObject getCurrent() {
    return json.add(OFFSETS_KEY, offsets).add(UPDATES_KEY, updates).build();
  }

  @Override
  public String getContentType() {
    return CONTENT_TYPE;
  }

  @Override
  public void handle(UpdateRecord update) {
    offsets.add(Integer.toString(update.getPartition()), Json.createValue(update.getOffset()));
    updates.add(update.getKey(), JsonObject.EMPTY_JSON_OBJECT);
  }

  @Override
  public String getContent() {
    return getCurrent().toString();
  }

  @Override
  public int getContentLength() {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public void getContent(OutputStream out) {
    throw new UnsupportedOperationException("not implemented");
  }

}
