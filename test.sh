#!/usr/bin/env bash
[ -z "$DEBUG" ] || set -x
set -eo pipefail
YBIN="$(dirname $0)"

# by default we don't run devservices based tests as they require docker
mvn test -Dx-test-exclude-groups=no-excludes
