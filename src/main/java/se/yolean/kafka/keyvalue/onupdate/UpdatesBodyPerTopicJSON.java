package se.yolean.kafka.keyvalue.onupdate;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonWriter;

import se.yolean.kafka.keyvalue.UpdateRecord;

// NOTE javax.json is unfit for what this class tried to do -- incrementally update a JSON.
// Unless we find a lib that is designed to keep state as json, not on-off conversions of object trees,
// we should drop this impl and go for the object tree strategy
public class UpdatesBodyPerTopicJSON implements UpdatesBodyPerTopic {

  public static final String CONTENT_TYPE = "application/json";

  public static final String VERSION_KEY = "v";
  public static final String TOPIC_KEY   = "topic";
  public static final String OFFSETS_KEY = "offsets";
  public static final String UPDATES_KEY = "updates";

  private JsonObjectBuilder builder;
  private JsonObjectBuilder offsets;
  private JsonObject offsetsBuilt = null;
  private JsonObjectBuilder updates;
  private JsonObjectBuilder json;

  Map<String,String> headers = new HashMap<String, String>(2);
  boolean built = false;

  public UpdatesBodyPerTopicJSON(String topicName) {
    builder = Json.createObjectBuilder();
    offsets = Json.createObjectBuilder();
    updates = Json.createObjectBuilder();
    json = builder.add(VERSION_KEY, 1).add(TOPIC_KEY, topicName);
    headers.put(UpdatesBodyPerTopic.HEADER_TOPIC, topicName);
  }

  JsonObject getCurrent() {
    if (built) {
      throw new IllegalStateException("Refusing to serialize content twice");
    }
    if (offsetsBuilt == null) getHeaders();
    built = true;
    return json.add(OFFSETS_KEY, offsetsBuilt).add(UPDATES_KEY, updates).build();
  }

  @Override
  public Map<String, String> getHeaders() {
    if (built) {
      throw new IllegalStateException("Refusing to return headers after content");
    }
    offsetsBuilt = offsets.build();
    headers.put(UpdatesBodyPerTopic.HEADER_OFFSETS, offsetsBuilt.toString());
    return headers;
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
  public byte[] getContent() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    getContent(out);
    return out.toByteArray();
  }

  @Override
  public void getContent(OutputStream out) {
    JsonWriter writer = Json.createWriter(out);
    writer.write(getCurrent());
  }

}
