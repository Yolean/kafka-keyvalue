#!/bin/sh
set -e
# TODO make jib build run the unit tests
# TODO invent a docker image with the tooling below, to make builds reproducible
gradle test
gradle jibDockerBuild --image=yolean/kafka-keyvalue:dev
build-contract
docker tag yolean/kafka-keyvalue:dev yolean/kafka-keyvalue:latest
docker push yolean/kafka-keyvalue:latest
