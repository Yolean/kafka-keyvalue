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

public interface OnUpdate {

  void pollStart(Iterable<String> topics);

  /**
   * @param update The new value (which may be the old value at a new offset)
   */
  void handle(UpdateRecord update);

  /**
   * For consistency the only update result that counts is that
   * all targets have acknowledged all updates.
   *
   * Retries are thus implied, and targets may deduplicate requests on topic+partition+offset.
   *
   * @throws RuntimeException on failure to get ack from onupdate targets,
   *   _suppressing_ consumer commit and triggering application exit/restart
   *   TODO or given that we run consumer in a thread, would we rather return a boolean false?
   */
  void pollEndBlockingUntilTargetsAck();

}
