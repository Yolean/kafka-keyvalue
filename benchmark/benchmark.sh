#!/usr/bin/env bash
[ -z "$DEBUG" ] || set -x
set -eo pipefail
WORKDIR="$(dirname $0)"
[ "$WORKDIR" != "." ] || WORKDIR="$PWD"

# NS=kkv-benchmark

# export KUBECONFIG="$WORKDIR/kubeconfig"
# echo "Hint: y-kubie ctx -f '$WORKDIR/kubeconfig'" -n $NS

# y-k3d cluster create kkv-benchmark || echo "Cluster already exists?"

# kubectl create namespace $NS
# kubectl config set-context --current --namespace=$NS

# kubectl -n $NS apply -k github.com/Yolean/kubernetes-kafka/variants/dev-small?ref=kafka-2.8.0

# skaffold dev --tail=true --port-forward=true --status-check=false

docker rm -f kkv-benchmark-kafka || true
docker build -t kafka-kraft:local kafka-kraft/
docker run -p 9094:9094 --name kkv-benchmark-kafka -ti kafka-kraft:local
