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

package se.yolean.kafka.keyvalue.onupdate.hc;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import se.yolean.kafka.keyvalue.onupdate.hc.UpdateTarget;

class UpdateTargetTest {

  @Test
  void test() {
    UpdateTarget updateTarget = new UpdateTarget("http://myhost.local:12345/whatever/uri?and-params=true");
    assertEquals("http://myhost.local:12345", updateTarget.getHttpclientContextHost().toString());
    assertEquals("/whatever/uri?and-params=true", updateTarget.getHttpUriFromHost("t1").toString());
  }

  @Test
  void testWithTopicNameInPath() {
    UpdateTarget updateTarget = new UpdateTarget("http://dev.local:123/updates/__TOPIC__/");
    assertEquals("http://dev.local:123", updateTarget.getHttpclientContextHost().toString());
    assertEquals("/updates/topic1/", updateTarget.getHttpUriFromHost("topic1").toString());
  }

  @Test
  void testWithTopicNameInQuery() {
    UpdateTarget updateTarget = new UpdateTarget("http://dev.local:123/updates?topic=__TOPIC__");
    assertEquals("http://dev.local:123", updateTarget.getHttpclientContextHost().toString());
    assertEquals("/updates?topic=Topic2", updateTarget.getHttpUriFromHost("Topic2").toString());
    assertEquals("http://dev.local:123/updates?topic=__TOPIC__", updateTarget.toString());
  }

}
