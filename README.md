
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

```
alias compose='docker-compose -f build-contracts/docker-compose.yml'
gradle jibDockerBuild --image=yolean/kafka-keyvalue:dev
compose up -d cache1
compose up smoketest
compose up --build example-nodejs-client
compose down
```

Note: `build-contract` (see [build-and-push.sh](./build-and-push.sh)) sometimes fails due to timing issues. Try re-running.

During development of the cache itself or the example nodejs client
it's more convenient to start only `kafka` and `pixy` through docker.

The main class is `se.yolean.kafka.keyvalue.cli.Main`.

Run the cache service from your IDE with args like: `--port 18081 --streams-props bootstrap.servers=localhost:19092 num.standby.replicas=0 --hostname localhost --topic topic1 --application-id kv-test1-local-001 --onupdate http://127.0.0.1:8081/kafka-keyvalue/v1/updates`

Test manually using for example `echo 'mytest={"t":1}' | kafkacat -b localhost:19092 -P -t topic1 -K '='; curl http://localhost:19081/cache/v1/raw/mytest`.

Run the nodejs locally using: `cd example-nodejs-client; npm ci; ./node_modules/.bin/jest --runInBand --watch `
(Note that the mock server for unupdate calls only exists during Jest runs)

## Logging

The distribution bundles log4j2, which we also use as logging API.
To configure per deployment, provide a file and set `-Dlog4j.configurationFile`.
For reconfigurability at runtime Kubernets configmaps could be used with [monitorInterval](https://logging.apache.org/log4j/2.x/manual/configuration.html#AutomaticReconfiguration).
