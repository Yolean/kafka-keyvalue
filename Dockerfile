FROM gradle:4.9.0-jdk8-slim@sha256:4987901ff570fdc61e964b9782d1dc3e81de4eb37bae91750213b10fc65bd831 \
  as build

WORKDIR /kafka-keyvalue

COPY build.gradle .

USER root
RUN rm /root/.gradle && mkdir -p src/test/java && echo 'class DummyTest { @org.junit.jupiter.api.Test void t() {} }' > src/test/java/DummyTest.java
RUN gradle build --no-daemon --no-parallel --no-build-cache --stacktrace

COPY . .

RUN gradle build --no-daemon --no-parallel --no-build-cache --stacktrace

FROM solsson/jdk-opensource:11.0.2@sha256:9088fd8eff0920f6012e422cdcb67a590b2a6edbeae1ff0ca8e213e0d4260cf8

WORKDIR /usr/src/app

COPY --from=build /kafka-keyvalue/build ./build

ENTRYPOINT ["java", "-cp", "build/libs/*", "se.yolean.kafka.keyvalue.cli.Main" ]
