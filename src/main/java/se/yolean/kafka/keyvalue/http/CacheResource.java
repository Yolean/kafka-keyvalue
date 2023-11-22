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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import jakarta.ws.rs.core.UriInfo;

import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.health.HealthCheckResponse.Status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.smallrye.common.annotation.Identifier;
import se.yolean.kafka.keyvalue.KafkaCache;
import se.yolean.kafka.keyvalue.onupdate.UpdatesBodyPerTopic;

@Path("/cache/v1")
public class CacheResource implements HealthCheck {

  @Inject
  ObjectMapper mapper;

  @Inject // Note that this can be null if cache is still in it's startup event handler
  @Identifier("kkv")
  KafkaCache cache = null;

  @Override
  public HealthCheckResponse call() {
    return HealthCheckResponse.named("REST liveness").up().build();
  }

  void requireUpToDateCache() throws javax.ws.rs.ServiceUnavailableException {
    if (cache == null) {
      throw new javax.ws.rs.ServiceUnavailableException("Denied because cache isn't started yet, check /health for status");
    }
    if (!cache.isReady()) {
      throw new javax.ws.rs.ServiceUnavailableException("Denied because cache is unready, check /health for status");
    }
  }

  /**
   * Will eventually contain logic for reading values from other replicas in
   * partitioned caches
   * (or at least so we thought back in the non-sidecar model).
   *
   * @param key To look up
   * @return the value
   * @throws NotFoundException If the key wasn't in the cache or if the value
   *                           somehow was null
   */
  byte[] getCacheValue(String key) throws NotFoundException {
    requireUpToDateCache();
    if (key == null) {
      throw new javax.ws.rs.BadRequestException("Request key can not be null");
    }
    if (key == "") {
      throw new javax.ws.rs.BadRequestException("Request key can not be empty");
    }
    final byte[] value = cache.getValue(key);
    if (value == null) {
      throw new NotFoundException();
    }
    return value;
  }

  @GET
  @Path("/raw/{key}")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public Response valueByKey(@PathParam("key") final String key, @Context UriInfo uriInfo) throws JsonProcessingException {
    requireUpToDateCache();

    var response = Response.ok(getCacheValue(key));

    applyOffsetHeaders(response);

    return response.build();
  }

  @GET
  @Path("/offset/{topic}/{partition}")
  @Produces(MediaType.TEXT_PLAIN)
  public Long getCurrentOffset(@PathParam("topic") String topic, @PathParam("partition") Integer partition) {
    if (topic == null) {
      throw new BadRequestException("Topic can not be null");
    }
    if (topic.length() == 0) {
      throw new BadRequestException("Topic can not be a zero length string");
    }
    if (partition == null) {
      throw new BadRequestException("Partition can not be null");
    }
    return cache.getCurrentOffset(topic, partition);
  }

  /**
   * All keys in this instance (none from the partitions not represented here),
   * newline separated.
   */
  @GET
  @Path("/keys")
  public Response keys() throws IOException {
    requireUpToDateCache();
    Iterator<String> all = cache.getKeys();

    ResponseBuilder response = Response.status(200);
    applyOffsetHeaders(response);
    response.entity(all);
    return response.build();
  }

  /**
   * @return Newline separated values (no keys)
   * @throws IOException
   */
  @GET
  @Path("/values")
  @Produces(MediaType.TEXT_PLAIN)
  public Response values() throws IOException {
    requireUpToDateCache();
    Iterator<byte[]> values = cache.getValues();

    ResponseBuilder response = Response.status(200);
    applyOffsetHeaders(response);
    response.entity(values);
    return response.build();
  }

  private void applyOffsetHeaders(ResponseBuilder response) throws JsonProcessingException {
    var offsets = cache.getCurrentOffsets();
    var value = mapper.writeValueAsString(offsets);
    response.header(UpdatesBodyPerTopic.HEADER_PREFIX + "last-seen-offsets", value);
  }

}
