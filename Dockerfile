FROM yolean/builder-quarkus:907bcbc85d22a29d3243e2af97a0b09fba2ee4ce@sha256:91ef470b901eb6a0031f278f4a04d26ee1844f514b08826f5e7c16d661d8525d \
  as dev

COPY pom.xml .
RUN y-build-quarkus-cache

COPY --chown=nonroot:nogroup . .

# https://github.com/quarkusio/quarkus/blob/1.13.1.Final/extensions/kafka-client/deployment/src/main/java/io/quarkus/kafka/client/deployment/KafkaProcessor.java#L194
# https://github.com/quarkusio/quarkus/blob/1.13.1.Final/extensions/kafka-client/runtime/src/main/java/io/quarkus/kafka/client/runtime/KafkaRecorder.java#L23
RUN mkdir -p src/main/resources/org/xerial/snappy/native/Linux/x86_64 \
  && cp -v /usr/lib/x86_64-linux-gnu/jni/libsnappyjava.so src/main/resources/org/xerial/snappy/native/Linux/x86_64/libsnappyjava.so \
  && ldd -v src/main/resources/org/xerial/snappy/native/Linux/x86_64/libsnappyjava.so

ENTRYPOINT [ "mvn", "compile", "quarkus:dev" ]
CMD [ "-Dquarkus.http.host=0.0.0.0", "-Dquarkus.http.port=8090" ]

# The jar and the lib folder is required for the jvm target even when the native target is the end result
# MUST be followed by a real build, or we risk pushing images despite test failures
RUN mvn package -Dmaven.test.skip=true

# For a regular JRE image run: docker build --build-arg build="package" --target=jvm
ARG build="package -Pnative"

RUN mvn --batch-mode $build

FROM yolean/java:907bcbc85d22a29d3243e2af97a0b09fba2ee4ce@sha256:63674354bd7f6f6660af89b483df98124c7d3062ce1e59a12ec012a47be769a3 \
  as jvm
ARG SOURCE_COMMIT
ARG SOURCE_BRANCH
ARG IMAGE_NAME

WORKDIR /app
COPY --from=dev /workspace/target/quarkus-app /app

EXPOSE 8090
ENTRYPOINT [ "java", \
  "-Dquarkus.http.host=0.0.0.0", \
  "-Dquarkus.http.port=8090", \
  "-Djava.util.logging.manager=org.jboss.logmanager.LogManager", \
  "-jar", "quarkus-run.jar" ]

ENV SOURCE_COMMIT=${SOURCE_COMMIT} SOURCE_BRANCH=${SOURCE_BRANCH} IMAGE_NAME=${IMAGE_NAME}

FROM yolean/runtime-quarkus-ubuntu:907bcbc85d22a29d3243e2af97a0b09fba2ee4ce@sha256:d192704054b0eb6e089f379a77f83f90fb9ad5061e9748910ce48887766e8b81

COPY --from=dev /workspace/target/*-runner /usr/local/bin/quarkus

EXPOSE 8090
CMD ["-Dquarkus.http.host=0.0.0.0", "-Dquarkus.http.port=8090"]

ARG SOURCE_COMMIT
ARG SOURCE_BRANCH
ARG IMAGE_NAME

ENV SOURCE_COMMIT=${SOURCE_COMMIT} SOURCE_BRANCH=${SOURCE_BRANCH} IMAGE_NAME=${IMAGE_NAME}
