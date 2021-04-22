#!/usr/bin/env bash
set -x

ID=$(./bin/kafka-storage.sh random-uuid | tail -n 1)

./bin/kafka-storage.sh format -t $ID -c ./config/server.properties

./bin/kafka-server-start.sh ./config/server.properties
