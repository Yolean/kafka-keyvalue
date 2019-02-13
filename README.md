
## Design choices

 * OnUpdate does not include message value because it could mean that consumers get old values, for example at retries or restarts.
 * Restricted to a single replica per application ID at the moment. Based on [bakdata/kafka-key-value-store](https://medium.com/bakdata/queryable-kafka-topics-with-kafka-streams-8d2cca9de33f) we can add support for patitioned stores later.

## Constraints

 * Topic keys must be deserializable as [String](https://kafka.apache.org/21/javadoc/org/apache/kafka/common/serialization/Serdes.html#String--) because these strings are used in REST URIs.
