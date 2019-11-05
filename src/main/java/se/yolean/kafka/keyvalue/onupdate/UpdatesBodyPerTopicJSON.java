// Copyright 2019 Yolean AB
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

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

  ByteArrayOutputStream alreadySerializedToMemory = null;

  public UpdatesBodyPerTopicJSON(String topicName) {
    builder = Json.createObjectBuilder();
    offsets = Json.createObjectBuilder();
    updates = Json.createObjectBuilder();
    json = builder.add(VERSION_KEY, 1).add(TOPIC_KEY, topicName);
    headers.put(UpdatesBodyPerTopic.HEADER_TOPIC, topicName);
  }

  JsonObject getCurrent() {
    if (offsetsBuilt == null) {
      throw new IllegalStateException("Headers must be retrieved before body");
    }
    return json.add(OFFSETS_KEY, offsetsBuilt).add(UPDATES_KEY, updates).build();
  }

  @Override
  public Map<String, String> getHeaders() {
    if (offsetsBuilt == null) {
      offsetsBuilt = offsets.build();
      headers.put(UpdatesBodyPerTopic.HEADER_OFFSETS, offsetsBuilt.toString());
    }
    return headers;
  }

  @Override
  public String getContentType() {
    return CONTENT_TYPE;
  }

  @Override
  public void handle(UpdateRecord update) {
    if (offsetsBuilt != null) {
      throw new IllegalStateException("This update has already been retrieved for dispatch and can no longer be updated");
    }
    offsets.add(Integer.toString(update.getPartition()), Json.createValue(update.getOffset()));
    updates.add(update.getKey(), JsonObject.EMPTY_JSON_OBJECT);
  }

  @Override
  public byte[] getContent() {
    if (alreadySerializedToMemory == null) {
      alreadySerializedToMemory = new ByteArrayOutputStream();
      getContent(alreadySerializedToMemory);
    }
    return alreadySerializedToMemory.toByteArray();
  }

  @Override
  public void getContent(OutputStream out) {
    JsonWriter writer = Json.createWriter(out);
    writer.write(getCurrent());
  }

}
