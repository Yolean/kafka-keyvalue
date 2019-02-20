#!/bin/sh
set -e
# TODO make jib build run the unit tests

# If all tooling is available locally use
#gradlejibdocker=gradle
#contract=build-contract
gradlejibdocker="docker run --rm -v $(pwd):/workspace -v /var/run/docker.sock:/var/run/docker.sock solsson/gradle-jib-docker@sha256:390f765ba4c8423e30ae1668bfd2e74f026a11b5ec3f0bae23bd36b0ed4c0c75 gradle --no-daemon --no-parallel --no-build-cache"
contract="docker run -v /var/run/docker.sock:/var/run/docker.sock -v $(pwd)/:/source  --rm --name kafka-keyvalue-build solsson/build-contract@sha256:961624a502c4bf64bdec328e65a911a2096192e7c1a268d7360b9c85ae7a35b8"

$gradlejibdocker --stacktrace test
$gradlejibdocker --stacktrace jibDockerBuild --image=yolean/kafka-keyvalue:dev

$contract test

docker tag yolean/kafka-keyvalue:dev yolean/kafka-keyvalue:latest
docker push yolean/kafka-keyvalue:latest
