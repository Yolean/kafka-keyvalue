FROM --platform=$TARGETPLATFORM docker.io/yolean/builder-quarkus:43013261fdd8e4da16cf29f8336c0fc522db5ee3@sha256:810a23f91afec784a73ff9de32489c33fbc749b753e417619327da0f194a6af8 \
  as jnilib

# https://github.com/xerial/snappy-java/blob/master/src/main/java/org/xerial/snappy/OSInfo.java#L113
RUN set -ex; \
  curl -o snappy.jar -sLSf https://repo1.maven.org/maven2/org/xerial/snappy/snappy-java/1.1.9.0/snappy-java-1.1.9.0.jar; \
  LIBPATH=$(java -cp snappy.jar org.xerial.snappy.OSInfo); \
  ARCH=$(java -cp snappy.jar org.xerial.snappy.OSInfo --arch); \
  mkdir -pv native/$LIBPATH; \
  cp -v /usr/lib/$ARCH-linux-gnu/jni/* native/$LIBPATH/

FROM --platform=$TARGETPLATFORM docker.io/yolean/builder-quarkus:43013261fdd8e4da16cf29f8336c0fc522db5ee3@sha256:810a23f91afec784a73ff9de32489c33fbc749b753e417619327da0f194a6af8 \
  as dev

COPY pom.xml .
RUN y-build-quarkus-cache

COPY --chown=nonroot:nogroup . .

# https://github.com/quarkusio/quarkus/blob/1.13.1.Final/extensions/kafka-client/deployment/src/main/java/io/quarkus/kafka/client/deployment/KafkaProcessor.java#L194
# https://github.com/quarkusio/quarkus/blob/2.7.1.Final/extensions/kafka-client/deployment/src/main/java/io/quarkus/kafka/client/deployment/KafkaProcessor.java#L268
# https://github.com/quarkusio/quarkus/blob/1.13.1.Final/extensions/kafka-client/runtime/src/main/java/io/quarkus/kafka/client/runtime/KafkaRecorder.java#L23
# https://github.com/quarkusio/quarkus/blob/2.7.1.Final/extensions/kafka-client/runtime/src/main/java/io/quarkus/kafka/client/runtime/KafkaRecorder.java#L26
# TODO check that for "$build" == "native-image" TARGETPLATFORM == BUILDPLATFORM
COPY --from=jnilib /workspace/native/Linux rest/src/main/resources/org/xerial/snappy/native/Linux
# TODO need to verify?
#RUN ldd -v rest/src/main/resources/org/xerial/snappy/native/Linux/x86_64/libsnappyjava.so

ENTRYPOINT [ "mvn", "compile", "quarkus:dev" ]
CMD [ "-Dquarkus.http.host=0.0.0.0" ]

# The jar and the lib folder is required for the jvm target even when the native target is the end result
# MUST be followed by a real build, or we risk pushing images despite test failures
RUN mvn package -Dmaven.test.skip=true

# For a regular JRE image run: docker build --build-arg build="package" --target=jvm
ARG build="package -Pnative"

RUN mvn --batch-mode $build

FROM --platform=$TARGETPLATFORM docker.io/yolean/runtime-quarkus-ubuntu-jre:43013261fdd8e4da16cf29f8336c0fc522db5ee3@sha256:e46a9bb44952ef82765158f86376d686c6c5835aff9080a5adb50296fb4ed712 \
  as jvm

WORKDIR /app
COPY --from=dev /workspace/target/quarkus-app /app

EXPOSE 8090
ENTRYPOINT [ "java", \
  "-Dquarkus.http.host=0.0.0.0", \
  "-Djava.util.logging.manager=org.jboss.logmanager.LogManager", \
  "-jar", "quarkus-run.jar" ]

FROM --platform=$TARGETPLATFORM docker.io/yolean/runtime-quarkus-ubuntu:43013261fdd8e4da16cf29f8336c0fc522db5ee3@sha256:e560bc7e18dbcf80ca325afb4a1e54df78b80ed7a064cfeb08da8e71d4f0cf65

COPY --from=dev /workspace/target/*-runner /usr/local/bin/quarkus

EXPOSE 8090
CMD [ "-Dquarkus.http.host=0.0.0.0" ]
