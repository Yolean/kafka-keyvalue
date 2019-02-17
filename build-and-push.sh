#!/bin/sh
set -e
# TODO make jib build run the unit tests
# If all tooling is available locally use
#gradlejibdocker=gradle
gradlejibdocker="docker run --rm -v $(pwd):/workspace -v /var/run/docker.sock:/var/run/docker.sock solsson/gradle-jib-docker:latest gradle --no-daemon --no-parallel --no-build-cache"
$gradlejibdocker --stacktrace test
$gradlejibdocker --stacktrace jibDockerBuild --image=yolean/kafka-keyvalue:dev
build-contract
docker tag yolean/kafka-keyvalue:dev yolean/kafka-keyvalue:latest
docker push yolean/kafka-keyvalue:latest
