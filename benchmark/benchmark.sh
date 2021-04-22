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
docker run -p 9094:9094 --name kkv-benchmark-kafka --rm -d kafka-kraft:local

echo "Running benchmark prepare"
# quarkus:dev with a main-class that terminates ends up here:
# https://github.com/quarkusio/quarkus/blob/c87b6fae3db21b10e225f81936d848eb05c09fec/core/deployment/src/main/java/io/quarkus/deployment/dev/IsolatedDevModeMain.java#L114
#mvn compile quarkus:dev -Dquarkus.package.main-class=prepare
mvn package -Dmaven.test.skip=true
java -jar target/quarkus-app/quarkus-run.jar

echo "Kafka starting..."
echo ""
echo "Starting kkv in dev mode ..."
echo "(the benchmark app can be started in a new shell using: mvn quarkus:dev -Dquarkus.package.main-class=)"
echo ""
(cd ../; mvn quarkus:dev \
  -Dquarkus.http.port=8090 \
  -Dkafka_bootstrap=localhost:9094 \
  -Dtopic=kkv-benchmark \
  -Dkafka_group_id=kkv1 \
  -Dkafka_offset_reset=earliest \
  -Dtarget=http://localhost:8080/kkv-benchmark/onupdate \
  )
