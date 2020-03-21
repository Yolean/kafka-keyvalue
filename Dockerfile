FROM solsson/kafka:graalvm-latest@sha256:1bda0d8bbb0baf089e6ce898247a9560ae26967aedf84f33208902a865694cfd \
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
  mvn package; \
  mvn package -Pnative || echo "# Build error is expected. Caching dependencies."; \
  cd ..; \
  rm -r kafka-quickstart;

COPY . .

ENTRYPOINT [ "mvn", "compile", "quarkus:dev" ]
CMD [ "-Dquarkus.http.host=0.0.0.0", "-Dquarkus.http.port=8090" ]

# The jar and the lib folder is required for the jvm target even when the native target is the end result
RUN mvn package -Dmaven.test.skip=true

# For a regular JRE image run: docker build --build-arg build="package" --target=jvm
ARG build="package -Pnative"

RUN mvn $build

FROM solsson/kafka:jre-latest@sha256:4f880765690d7240f4b792ae16d858512cea89ee3d2a472b89cb22c9b5d5bd66 \
  as jvm
ARG SOURCE_COMMIT
ARG SOURCE_BRANCH
ARG IMAGE_NAME

WORKDIR /app
COPY --from=dev /workspace/target/lib ./lib
COPY --from=dev /workspace/target/*-runner.jar ./quarkus-kafka.jar

EXPOSE 8090
ENTRYPOINT [ "java", \
  "-Dquarkus.http.host=0.0.0.0", \
  "-Dquarkus.http.port=8090", \
  "-Djava.util.logging.manager=org.jboss.logmanager.LogManager", \
  "-cp", "./lib/*", \
  "-jar", "./quarkus-kafka.jar" ]

ENV SOURCE_COMMIT=${SOURCE_COMMIT} SOURCE_BRANCH=${SOURCE_BRANCH} IMAGE_NAME=${IMAGE_NAME}

# The rest should be identical to src/main/docker/Dockerfile.native
FROM registry.access.redhat.com/ubi8/ubi-minimal:8.1@sha256:01b8fb7b3ad16a575651a4e007e8f4d95b68f727b3a41fc57996be9a790dc4fa
# Do we want compression libs at runtime?

WORKDIR /work/
COPY --from=dev /workspace/target/*-runner /work/application

# set up permissions for user `1001`
RUN chmod 775 /work /work/application \
  && chown -R 1001 /work \
  && chmod -R "g+rwX" /work \
  && chown -R 1001:root /work

EXPOSE 8090
ENTRYPOINT ["./application", "-Djava.util.logging.manager=org.jboss.logmanager.LogManager"]
CMD ["-Dquarkus.http.host=0.0.0.0", "-Dquarkus.http.port=8090"]

ARG SOURCE_COMMIT
ARG SOURCE_BRANCH
ARG IMAGE_NAME

ENV SOURCE_COMMIT=${SOURCE_COMMIT} SOURCE_BRANCH=${SOURCE_BRANCH} IMAGE_NAME=${IMAGE_NAME}
