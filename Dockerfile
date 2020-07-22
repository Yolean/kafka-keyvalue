FROM solsson/kafka:graalvm@sha256:28505c768b7f8b44168b9df5bc27dc4735d1be75ea5a12eb94c34af2d661e66a \
  as dev

WORKDIR /workspace
COPY pom.xml .
RUN set -e; \
  export QUARKUS_VERSION=$(cat pom.xml | grep '<quarkus.platform.version>' | sed 's/.*>\(.*\)<.*/\1/'); \
  echo "Quarkus version: $QUARKUS_VERSION"; \
  mv pom.xml pom.tmp; \
  mvn io.quarkus:quarkus-maven-plugin:$QUARKUS_VERSION:create \
    -DprojectGroupId=org.example.temp \
    -DprojectArtifactId=kafka-quickstart \
    -Dextensions="kafka"; \
  mv pom.tmp kafka-quickstart/pom.xml; \
  cd kafka-quickstart; \
  mkdir -p src/test/java/org && echo 'package org; public class T { @org.junit.jupiter.api.Test public void t() { } }' > src/test/java/org/T.java; \
  mvn --batch-mode package; \
  mvn --batch-mode package -Pnative -Dquarkus.native.additional-build-args=--dry-run \
  || echo "= BUILD ERROR IS OK: Caching dependencies."; \
  cd ..; \
  rm -r kafka-quickstart;

COPY . .

ENTRYPOINT [ "mvn", "compile", "quarkus:dev" ]
CMD [ "-Dquarkus.http.host=0.0.0.0", "-Dquarkus.http.port=8090" ]

# The jar and the lib folder is required for the jvm target even when the native target is the end result
# MUST be followed by a real build, or we risk pushing images despite test failures
RUN mvn package -Dmaven.test.skip=true

# For a regular JRE image run: docker build --build-arg build="package" --target=jvm
ARG build="package -Pnative"

RUN mvn --batch-mode $build | tee build.log; \
  set -ex; \
  grep '[INFO] BUILD SUCCESS' build.log || \
    grep 'Native memory allocation (mmap) failed\|Exit code was 137 which indicates an out of memory error' build.log && \
    grep --color=never 'NativeImageBuildStep] /opt/graalvm' build.log | cut -d ' ' -f 3- | \
      (cd target/*-source-jar; sh - ); \
  rm build.log

FROM solsson/kafka:jre@sha256:9374540e6643ac577056e900872793cc4a96687025e3b492e93ad8d10c8e429b \
  as jvm
ARG SOURCE_COMMIT
ARG SOURCE_BRANCH
ARG IMAGE_NAME

WORKDIR /app
COPY --from=dev /workspace/target/lib ./lib
COPY --from=dev /workspace/target/*-runner.jar ./kafka-keyvalue.jar

EXPOSE 8090
ENTRYPOINT [ "java", \
  "-Dquarkus.http.host=0.0.0.0", \
  "-Dquarkus.http.port=8090", \
  "-Djava.util.logging.manager=org.jboss.logmanager.LogManager", \
  "-cp", "./lib/*", \
  "-jar", "./kafka-keyvalue.jar" ]

ENV SOURCE_COMMIT=${SOURCE_COMMIT} SOURCE_BRANCH=${SOURCE_BRANCH} IMAGE_NAME=${IMAGE_NAME}

FROM gcr.io/distroless/base-debian10:nonroot@sha256:78f2372169e8d9c028da3856bce864749f2bb4bbe39c69c8960a6e40498f8a88

COPY --from=dev \
  /lib/x86_64-linux-gnu/libz.so.* \
  /lib/x86_64-linux-gnu/

COPY --from=dev \
  /usr/lib/x86_64-linux-gnu/libzstd.so.* \
  /usr/lib/x86_64-linux-gnu/libsnappy.so.* \
  /usr/lib/x86_64-linux-gnu/liblz4.so.* \
  /usr/lib/x86_64-linux-gnu/

COPY --from=dev /workspace/target/*-runner /usr/local/bin/kafka-keyvalue

EXPOSE 8090
ENTRYPOINT ["kafka-keyvalue", "-Djava.util.logging.manager=org.jboss.logmanager.LogManager"]
CMD ["-Dquarkus.http.host=0.0.0.0", "-Dquarkus.http.port=8090"]

ARG SOURCE_COMMIT
ARG SOURCE_BRANCH
ARG IMAGE_NAME

ENV SOURCE_COMMIT=${SOURCE_COMMIT} SOURCE_BRANCH=${SOURCE_BRANCH} IMAGE_NAME=${IMAGE_NAME}
