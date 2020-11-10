FROM yolean/builder-quarkus:b0095ce7f26bf362a9adb8fe4670ad3c038ae932@sha256:c14ddaa7d79af07ef788cf87df5ea69a9494f7385fd9426659aca2ff84376196 \
  as dev

COPY pom.xml .
RUN y-build-quarkus-cache

COPY . .

ENTRYPOINT [ "mvn", "compile", "quarkus:dev" ]
CMD [ "-Dquarkus.http.host=0.0.0.0", "-Dquarkus.http.port=8090" ]

# The jar and the lib folder is required for the jvm target even when the native target is the end result
# MUST be followed by a real build, or we risk pushing images despite test failures
RUN mvn package -Dmaven.test.skip=true

# For a regular JRE image run: docker build --build-arg build="package" --target=jvm
ARG build="package -Pnative"

RUN mvn --batch-mode $build

FROM yolean/java:f63772d02556021dbcb9f49fb9eff3d3dbe1b636@sha256:1bc5b3456a64fb70c85825682777c55a0999d9be56aca9bb1f507fe9b9171f83 \
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

FROM yolean/runtime-quarkus:b0095ce7f26bf362a9adb8fe4670ad3c038ae932@sha256:34abf0213998f11fcca26b9a3313577d0cc0c442b7df3669d200d2dc10f6436c

COPY --from=dev /workspace/target/*-runner /usr/local/bin/quarkus

EXPOSE 8090
CMD ["-Dquarkus.http.host=0.0.0.0", "-Dquarkus.http.port=8090"]

ARG SOURCE_COMMIT
ARG SOURCE_BRANCH
ARG IMAGE_NAME

ENV SOURCE_COMMIT=${SOURCE_COMMIT} SOURCE_BRANCH=${SOURCE_BRANCH} IMAGE_NAME=${IMAGE_NAME}
