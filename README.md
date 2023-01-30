
# Kafka key-value cache

For the sidecar-only maintenance branch see the [1.x](https://github.com/Yolean/kafka-keyvalue/tree/1.x) branch.

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

```
NOPUSH=true ./hooks/build
```

## Combine to a multi-arch image

1. Build and push on OSX: `DEBUG=true ./hooks/build`
2. Build and happy-push on Linux amd64: `DEBUG=true NOPUSH=true ./hooks/build`
3. The following, depending on platform:

```
cat multiarch-native.Dockerfile | docker buildx build --platform=linux/amd64,linux/arm64/v8 \
  --build-arg=SOURCE_COMMIT="$SOURCE_COMMIT" -t yolean/kafka-keyvalue:$SOURCE_COMMIT --push -
# Or
cat multiarch-native.Dockerfile | nerdctl build --platform=linux/amd64,linux/arm64/v8 \
  --build-arg=SOURCE_COMMIT="$SOURCE_COMMIT" -t yolean/kafka-keyvalue:$SOURCE_COMMIT -
nerdctl push --platform=linux/amd64,linux/arm64/v8 yolean/kafka-keyvalue:$SOURCE_COMMIT
```

## Logging

See [Quarkus' logging configuration](https://quarkus.io/guides/logging-guide).
