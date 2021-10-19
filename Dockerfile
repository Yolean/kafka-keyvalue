FROM docker.io/yolean/builder-quarkus-mvncache:41aefcc2461206b29e4605777ad15669f9d2dd1f@sha256:49f4ad1af955a33782b742407e16a7321d0bebb5b18d998362865df39a57105a \
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
CMD [ "-Dquarkus.http.host=0.0.0.0" ]

# The jar and the lib folder is required for the jvm target even when the native target is the end result
# MUST be followed by a real build, or we risk pushing images despite test failures
RUN mvn package -Dmaven.test.skip=true

# For a regular JRE image run: docker build --build-arg build="package" --target=jvm
ARG build="package -Pnative"

RUN mvn --batch-mode $build

FROM yolean/java:41aefcc2461206b29e4605777ad15669f9d2dd1f@sha256:944b0975dd1002ff4b3fd451f8e51c3927253c1bd1ea71df0e148bb612ef9e7a \
  as jvm
ARG SOURCE_COMMIT
ARG SOURCE_BRANCH
ARG IMAGE_NAME

WORKDIR /app
COPY --from=dev /workspace/target/quarkus-app /app

EXPOSE 8090
ENTRYPOINT [ "java", \
  "-Dquarkus.http.host=0.0.0.0", \
  "-Djava.util.logging.manager=org.jboss.logmanager.LogManager", \
  "-jar", "quarkus-run.jar" ]

ENV SOURCE_COMMIT=${SOURCE_COMMIT} SOURCE_BRANCH=${SOURCE_BRANCH} IMAGE_NAME=${IMAGE_NAME}

FROM docker.io/yolean/runtime-quarkus-ubuntu:41aefcc2461206b29e4605777ad15669f9d2dd1f@sha256:0fafc576c2b530b10612fc1baa079a023cb757c66d47938782d70ece8e121859

COPY --from=dev /workspace/target/*-runner /usr/local/bin/quarkus

EXPOSE 8090
CMD [ "-Dquarkus.http.host=0.0.0.0" ]

ARG SOURCE_COMMIT
ARG SOURCE_BRANCH
ARG IMAGE_NAME

ENV SOURCE_COMMIT=${SOURCE_COMMIT} SOURCE_BRANCH=${SOURCE_BRANCH} IMAGE_NAME=${IMAGE_NAME}
