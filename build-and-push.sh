#!/bin/sh
set -e

# github.com/Yolean/build-contract local or in-docker
#contract=build-contract
contract="docker run -v /var/run/docker.sock:/var/run/docker.sock -v $(pwd)/:/source  --rm --name kafka-keyvalue-build solsson/build-contract@sha256:961624a502c4bf64bdec328e65a911a2096192e7c1a268d7360b9c85ae7a35b8"

docker build -t yolean/kafka-keyvalue:dev .

$contract test

docker tag yolean/kafka-keyvalue:dev yolean/kafka-keyvalue:latest
docker push yolean/kafka-keyvalue:latest
