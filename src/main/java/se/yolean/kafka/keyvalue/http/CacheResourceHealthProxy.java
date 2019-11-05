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

package se.yolean.kafka.keyvalue.http;

import javax.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

@Liveness
public class CacheResourceHealthProxy implements HealthCheck {

  private CacheResource resource;

  @Inject
  public CacheResourceHealthProxy(CacheResource resource) {
    this.resource = resource;
  }

  @Override
  public HealthCheckResponse call() {
    if (this.resource == null) {
      return HealthCheckResponse.builder()
          .name("REST resource existence, not really endpoint liveness")
          .down().build();
    }
    // This is also not liveness.
    // Maybe Microprofile Health 2.0 is an abstraction too far from liveness, though good for readiness.
    return this.resource.call();
  }

}
