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

  byte[] getContent();

  /**
   * @param out UTF-8 stream
   */
  void getContent(OutputStream out);

}
