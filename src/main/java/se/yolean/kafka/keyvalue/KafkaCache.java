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
import java.util.List;


/**
 * The read-access contract for external API.
 */
public interface KafkaCache {

  public enum Stage {
    Created (10),
    //CreatingConsumer (20),
    //Initializing (30),
    //WaitingForKafkaConnection (40),
    Assigning (50),
    Resetting (60),
    //InitialPoll (70),
    PollingHistorical (80),
    Polling (90);

    final int metricValue;
    Stage(int metricValue) {
      this.metricValue = metricValue;
    }
  }

  Stage getStage();

  boolean isEndOffsetsReached();

  byte[] getValue(String key);

  /**
   * @param topicName
   * @param partition
   * @return offset for latest update, or null if the topic hasn't got updates
   */
  Long getCurrentOffset(String topicName, int partition);

  List<TopicPartitionOffset>getCurrentOffsets();

  Iterator<String> getKeys();

  Iterator<byte[]> getValues();

}