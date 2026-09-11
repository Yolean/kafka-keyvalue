
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
y-skaffold build -p prod-build --file-output=images-native.json --cache-artifacts=false
```

## Logging

See [Quarkus' logging configuration](https://quarkus.io/guides/logging-guide).

# KKV Node.js Client

Installable from https://www.npmjs.com/package/@yolean/kafka-keyvalue

Implements the [kafka-cache](https://github.com/Yolean/kafka-cache/) interface but backed by [KKV](https://github.com/Yolean/kafka-keyvalue/).

## Resilience of a consumer (1.9)

A consumer keeps its own copy of the topic: `streamValues` once at start, then one
`get` per key named by kkv's onupdate webhook. Since 1.9 that copy survives kkv being
absent or refusing:

- `updateListener` never rejects. It is bound to a plain EventEmitter, so a rejection
  used to surface as the process's `unhandledRejection`.
- A key whose fetch failed is retried with backoff until it succeeds (`updateRetry`,
  default 5 s doubling to 60 s; `false` for the 1.8 behaviour of staying stale until the
  next update names the key). A 404 that outlasts `get`'s own retries is the cache's
  answer and is not retried; `get` throws `NotFoundError` for it.
- Keys of one update are fetched one at a time, since every consumer of a topic gets
  the same push at once and kkv has a small connection cap.
- `streamValuesWhenReady(onValue, { retryIntervalMs })` polls readiness and retries the
  stream until it completes, for a consumer that cannot serve without the values.
- `createMetrics` adds `kafka_key_value_updates_received_total`,
  `kafka_key_value_update_failures_total{cause}` and
  `kafka_key_value_update_pending_keys`; `close()` unsubscribes and cancels retries.

Every request opens its own connection (node-fetch 2 sends `Connection: close` without
an agent), so a retry re-enters the Service's backend pick instead of returning to the
replica that just failed. Do not add a pooled agent without revisiting that.
