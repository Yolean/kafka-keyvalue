#!/bin/sh
set -e
gradle jibDockerBuild --image=yolean/kafka-keyvalue:dev
build-contract
docker tag yolean/kafka-keyvalue:dev yolean/kafka-keyvalue:latest
docker push yolean/kafka-keyvalue:latest
