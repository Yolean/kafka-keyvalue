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

import java.util.HashMap;
import java.util.Map;

import javax.enterprise.inject.Produces;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cache types
 * {@value #CACHE_TYPE_INMEMORY}
 */
@Singleton
public class ConfigureCache implements Provider<Map<String, byte[]>> {

  // ConfigProperty didn't work with Enum
  public static final String CACHE_TYPE_INMEMORY = "inmemory";

  final Logger logger = LoggerFactory.getLogger(this.getClass());

  @ConfigProperty(name="cache_initial_size", defaultValue="0")
  int initialSize;

  @ConfigProperty(name="cache_type", defaultValue=CACHE_TYPE_INMEMORY)
  String cacheType;

  @Produces
  //@javax.inject.Named("cache")
  @Override
  public Map<String, byte[]> get() {
    if (CACHE_TYPE_INMEMORY.equals(cacheType)) {
      logger.info("Providing new in-memory cache, initial size {}", initialSize);
      return new HashMap<String, byte[]>(initialSize);
    }
    throw new RuntimeException("Unsupported cache type: " + cacheType);
  }

}
