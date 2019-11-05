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

import javax.inject.Singleton;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import javax.inject.Inject;

/**
 * Relays the {@link #call()} because @Liveness and @Singleton
 * combined would break bean discovery on ConsumerAtLeastOnce.
 *
 * Also we want to handle the not-started-yet condition.
 */
@Readiness // Consumer's call() is the essential check for cache being warm, i.e. not liveness.
@Singleton
public class ConsumerAtLeastOnceHealthProxy implements HealthCheck {

  @Inject // Note that this can be null if cache is still in it's startup event handler
  ConsumerAtLeastOnce consumer;

  @Override
  public HealthCheckResponse call() {
    if (consumer == null) {
      return HealthCheckResponse.named("consume-loop").withData("stage", "NotStarted").build();
    }
    return consumer.call();
  }

}
