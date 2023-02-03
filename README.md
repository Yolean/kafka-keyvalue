
# Kafka key-value cache

## Example usage

See the `- name: kkv` sidecar in [the example yaml](kontrakt/kkv-example.yaml).

## Constraints

 * Topic keys must be deserializable as [String](https://kafka.apache.org/21/javadoc/org/apache/kafka/common/serialization/Serdes.html#String--) because these strings are used in REST URIs.

## Development

Use [Skaffold](), for example:

```bash
eval $(minikube docker-env)
kubectl apply -k github.com/Yolean/kubernetes-kafka/variants/dev-small?ref=v6.0.0
kubectl apply -f https://github.com/Yolean/kubernetes-kafka/raw/50345f266287861d7964d3339a2c2a28e79db2fe/variants/prometheus-operator-example/k8s-cluster-rbac.yaml
SKAFFOLD_NO_PRUNE=true skaffold dev
```

## Builds

JVM:

```
y-skaffold build --file-output=images-jvm.json
```

Single-arch native:

```
y-skaffold build --platform=linux/[choice-of-arch] -p prod-build --file-output=images-native.json
```

Multi-arch native
(expect 3 hrs build time on a 3 core 7Gi Buildkit with qemu):

```
y-skaffold build -p prod-build --file-output=images-native.json
```

## Logging

See [Quarkus' logging configuration](https://quarkus.io/guides/logging-guide).
