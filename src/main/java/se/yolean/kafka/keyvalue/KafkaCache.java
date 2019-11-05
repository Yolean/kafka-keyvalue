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

package se.yolean.kafka.keyvalue;

import java.util.Iterator;

/**
 * The read-access contract for external API.
 */
public interface KafkaCache {

  /**
   * Meant to be used with Kubernetes readiness probes to block use of the cache
   * during startup whey it may lag behind the topic.
   * Or if there's any other reason to suspect that the cache is unreliable.
   *
   * @return true if the cache can be considered up-to-date
   */
  boolean isReady();

  byte[] getValue(String key);

  /**
   * @param topicName
   * @param partition
   * @return offset for latest update, or null if the topic hasn't got updates
   */
  Long getCurrentOffset(String topicName, int partition);

  Iterator<String> getKeys();

  Iterator<byte[]> getValues();

}