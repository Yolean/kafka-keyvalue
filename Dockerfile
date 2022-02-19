FROM --platform=$TARGETPLATFORM docker.io/yolean/builder-quarkus:07bf9f634da62a9525a691924c64a4f79b1f10a5@sha256:054ef0a03ee06c3254213f6c0e2fe43023b396c1d841d528575cf41c096a367e \
  as jnilib

# https://github.com/xerial/snappy-java/blob/master/src/main/java/org/xerial/snappy/OSInfo.java#L113
RUN set -ex; \
  curl -o snappy.jar -sLSf https://repo1.maven.org/maven2/org/xerial/snappy/snappy-java/1.1.8.4/snappy-java-1.1.8.4.jar; \
  LIBPATH=$(java -cp snappy.jar org.xerial.snappy.OSInfo); \
  ARCH=$(java -cp snappy.jar org.xerial.snappy.OSInfo --arch); \
  mkdir -pv native/$LIBPATH; \
  cp -v /usr/lib/$ARCH-linux-gnu/jni/* native/$LIBPATH/

FROM --platform=$BUILDPLATFORM docker.io/yolean/builder-quarkus:07bf9f634da62a9525a691924c64a4f79b1f10a5@sha256:054ef0a03ee06c3254213f6c0e2fe43023b396c1d841d528575cf41c096a367e \
  as dev

COPY pom.xml .
#RUN y-build-quarkus-cache

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

FROM --platform=$TARGETPLATFORM docker.io/yolean/runtime-quarkus-ubuntu-jre:d091be226e9a62ee3cba9816cafedb8a06a17012@sha256:a4e85350a79341fe2216001ec500511066094ea0c387acb2f3627ca11951882c \
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

FROM --platform=$TARGETPLATFORM docker.io/yolean/runtime-quarkus-ubuntu:d091be226e9a62ee3cba9816cafedb8a06a17012@sha256:a4b4e77494c26720321b54a442b4806cdb59d426ae2266802bc5c8ed794dd2b6

COPY --from=dev /workspace/target/*-runner /usr/local/bin/quarkus

EXPOSE 8090
CMD [ "-Dquarkus.http.host=0.0.0.0" ]

ARG SOURCE_COMMIT
ARG SOURCE_BRANCH
ARG IMAGE_NAME

ENV SOURCE_COMMIT=${SOURCE_COMMIT} SOURCE_BRANCH=${SOURCE_BRANCH} IMAGE_NAME=${IMAGE_NAME}
