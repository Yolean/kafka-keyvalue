
# Kafka key-value cache

## Example usage

See [the Node.js example](./example-nodejs-client/cache-update-flow.spec.js).

## Design choices

 * OnUpdate does not include message value because it could mean that consumers get old values, for example at retries or restarts.
 * Restricted to a single replica per application ID at the moment. Based on [bakdata/kafka-key-value-store](https://medium.com/bakdata/queryable-kafka-topics-with-kafka-streams-8d2cca9de33f) we can add support for patitioned stores later.

## Constraints

 * Topic keys must be deserializable as [String](https://kafka.apache.org/21/javadoc/org/apache/kafka/common/serialization/Serdes.html#String--) because these strings are used in REST URIs.

## Running

Docker builds are available at [docker.io/kafka/keyvalue](https://hub.docker.com/r/yolean/kafka-keyvalue).

See examples of CLI args in [ArgsToOptionsTest](./src/test/java/se/yolean/kafka/keyvalue/cli/ArgsToOptionsTest.java).

For REST endpoints see `@Path` in [Endpoints](./src/main/java/se/yolean/kafka/keyvalue/Endpoints.java).

## Development

The [build-contract](https://github.com/Yolean/build-contract/) can be used as dev stack.

### Running locally

Note that compatiblity with [changes are automatically reflected](https://quarkus.io/guides/maven-tooling#development-mode) is yet to be verified.

```bash
alias compose='docker-compose -f build-contracts/docker-compose.yml'
compose up -d kafka pixy
topics=topic1 kafka_bootstrap=localhost:19092 kafka_group_id=dev1 kafka_offset_reset=latest mvn compile quarkus:dev
# in a different terminal
curl http://localhost:8080/health
curl http://localhost:8080/ready
echo "k1=v1" | kafkacat -b localhost:19092 -P -t topic1
echo "k1=v1" | kafkacat -b localhost:19092 -P -t topic1
```

Run the nodejs locally using: `cd example-nodejs-client; npm ci; ./node_modules/.bin/jest --runInBand --watch `
(Note that the mock server for unupdate calls only exists during Jest runs)

### With docker-compose

```bash
alias compose='docker-compose -f build-contracts/docker-compose.yml'
compose up --build -d cache1
compose up smoketest
compose up --build example-nodejs-client
compose down
```

## Logging

The distribution bundles log4j2, which we also use as logging API.
To configure per deployment, provide a file and set `-Dlog4j.configurationFile`.
For reconfigurability at runtime Kubernets configmaps could be used with [monitorInterval](https://logging.apache.org/log4j/2.x/manual/configuration.html#AutomaticReconfiguration).
