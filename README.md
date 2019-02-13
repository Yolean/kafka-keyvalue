
## Design choices

 * OnUpdate does not include message value because it could mean that consumers get old values, for example at retries or restarts.

## Constraints

 * Topic keys must be deserializable as [String](https://kafka.apache.org/21/javadoc/org/apache/kafka/common/serialization/Serdes.html#String--) because these strings are used in REST URIs.
