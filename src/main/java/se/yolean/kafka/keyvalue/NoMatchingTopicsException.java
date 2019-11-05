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

import java.util.List;
import java.util.Map;

import org.apache.kafka.common.PartitionInfo;

public class NoMatchingTopicsException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public NoMatchingTopicsException(List<String> wasLookingFor, Map<String, List<PartitionInfo>> butFound) {
    super("Broker returned " + (butFound.size() == 0 ?
      "an empty topic list" :
      butFound.size() + " topic names but the list didn't include all of " + wasLookingFor));
  }

}
