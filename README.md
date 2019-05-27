
# Kafka key-value cache

## Example usage

See the `- name: kkv` sidecar in [the example yaml](kontrakt/kkv-example.yaml).

Automated docker builds are available at [docker.io/solsson/kafka-keyvalue](https://hub.docker.com/r/solsson/kafka-keyvalue).

## Constraints

 * Topic keys must be deserializable as [String](https://kafka.apache.org/21/javadoc/org/apache/kafka/common/serialization/Serdes.html#String--) because these strings are used in REST URIs.

## Development

Use [Skaffold](), for example:

```bash
eval $(minikube docker-env)
kubectl apply -k github.com/Yolean/kubernetes-kafka/variants/dev-small?ref=v6.0.0
SKAFFOLD_NO_PRUNE=true skaffold dev
```

## Logging

See [Quarkus' logging configuration](https://quarkus.io/guides/logging-guide).
