FROM maven:3.6.3-jdk-8-slim@sha256:d8e01825867d1e4ddbb96e40b5f08183e2a7d7ff40521c49cd8e76e36d75d340 as maven

FROM adoptopenjdk/openjdk8:jdk8u232-b09-slim@sha256:3031fe397a55c182dc709c1d54529e58ef49c10c3becbc5eafc5dc9a9cf3ce4d \
  as dev

COPY --from=maven /usr/share/maven /usr/share/maven
RUN ln -s /usr/share/maven/bin/mvn /usr/bin/mvn
ENV MAVEN_HOME=/usr/share/maven
ENV MAVEN_CONFIG=/root/.m2

WORKDIR /workspace
RUN mvn io.quarkus:quarkus-maven-plugin:1.0.1.Final:create \
    -DprojectGroupId=org.example.temp \
    -DprojectArtifactId=kafka-quickstart \
    -Dextensions="kafka" && \
    cd kafka-quickstart && \
    mkdir -p src/test/java/org && echo 'package org; public class T { @org.junit.jupiter.api.Test public void t() { } }' > src/test/java/org/T.java
COPY pom.xml kafka-quickstart/
RUN cd kafka-quickstart && \
  mvn package && \
  cd .. && \
  rm -r kafka-quickstart
COPY . .

ENTRYPOINT [ "mvn", "compile", "quarkus:dev" ]
CMD [ "-Dquarkus.http.host=0.0.0.0", "-Dquarkus.http.port=8090" ]

FROM adoptopenjdk/openjdk8:jdk8u232-b09-slim@sha256:3031fe397a55c182dc709c1d54529e58ef49c10c3becbc5eafc5dc9a9cf3ce4d \
  as maven-build

COPY --from=maven /usr/share/maven /usr/share/maven
RUN ln -s /usr/share/maven/bin/mvn /usr/bin/mvn
ENV MAVEN_HOME=/usr/share/maven
ENV MAVEN_CONFIG=/root/.m2

COPY --from=dev ${MAVEN_HOME} ${MAVEN_HOME}
COPY --from=dev ${MAVEN_CONFIG} ${MAVEN_CONFIG}

WORKDIR /workspace
COPY . .
# Can't get integration tests to pass on docker hub, and can't get logs from them
#RUN mvn -o package
RUN mvn -o package -DskipTests

FROM fabric8/java-alpine-openjdk8-jre@sha256:a5d31f17d618032812ae85d12426b112279f02951fa92a7ff8a9d69a6d3411b1 \
  as runtime-plainjava
ARG SOURCE_COMMIT
ARG SOURCE_BRANCH
ARG IMAGE_NAME

RUN apk add --no-cache snappy snappy lz4 zstd

WORKDIR /app
COPY --from=maven-build /workspace/target/lib ./lib
COPY --from=maven-build /workspace/target/*-runner.jar ./quarkus-kafka.jar

EXPOSE 8090
ENTRYPOINT [ "java", \
  "-Dquarkus.http.host=0.0.0.0", \
  "-Dquarkus.http.port=8090", \
  "-Djava.util.logging.manager=org.jboss.logmanager.LogManager", \
  "-cp", "./lib/*", \
  "-jar", "./quarkus-kafka.jar" ]

ENV SOURCE_COMMIT=${SOURCE_COMMIT} SOURCE_BRANCH=${SOURCE_BRANCH} IMAGE_NAME=${IMAGE_NAME}

# https://github.com/quarkusio/quarkus/issues/2792
FROM oracle/graalvm-ce:19.2.1@sha256:5ab434f12dc1a17c15107defd03de13c969a516dde022df2f737ec6002e7e7e1 \
  as native-build
RUN gu install native-image

# https://github.com/quarkusio/quarkus/issues/2718
#RUN yum -y install snappy libzstd lz4 && yum clean all

WORKDIR /project
COPY --from=maven-build /workspace/target/lib ./lib
COPY --from=maven-build /workspace/target/*-runner.jar ./

# from Quarkus' maven plugin mvn package -Pnative -Dnative-image.docker-build=true
RUN native-image \
  -J-Dsun.nio.ch.maxUpdateArraySize=100 \
  -J-Djava.util.logging.manager=org.jboss.logmanager.LogManager \
  -J-Dvertx.logger-delegate-factory-class-name=io.quarkus.vertx.core.runtime.VertxLogDelegateFactory \
  -J-Dvertx.disableDnsResolver=true \
  -J-Dio.netty.leakDetection.level=DISABLED \
  -J-Dio.netty.allocator.maxOrder=1 \
  --initialize-at-build-time= \
  # commented out due to "Error: policy com.oracle.svm.core.genscavenge.CollectionPolicy cannot be instantiated."
  #-H:InitialCollectionPolicy=com.oracle.svm.core.genscavenge.CollectionPolicy$BySpaceAndTime \
  -jar kafka-keyvalue-1.0-SNAPSHOT-runner.jar \
  -J-Djava.util.concurrent.ForkJoinPool.common.parallelism=1 \
  -H:FallbackThreshold=0 \
  -H:+ReportExceptionStackTraces \
  -H:+PrintAnalysisCallTree \
  -H:-AddAllCharsets \
  -H:EnableURLProtocols=http \
  -H:+JNI \
  --no-server \
  -H:-UseServiceLoaderFeature \
  -H:+TraceClassInitialization \
  -H:+StackTrace

# The rest should be identical to src/main/docker/Dockerfile which is the recommended quarkus build
FROM registry.access.redhat.com/ubi8/ubi-minimal@sha256:32fb8bae553bfba2891f535fa9238f79aafefb7eff603789ba8920f505654607
ARG SOURCE_COMMIT
ARG SOURCE_BRANCH
ARG IMAGE_NAME

WORKDIR /work/
COPY --from=native-build /project/*-runner /work/application
#RUN chmod 775 /work
EXPOSE 8090
ENTRYPOINT ["./application", "-Djava.util.logging.manager=org.jboss.logmanager.LogManager"]
CMD ["-Dquarkus.http.host=0.0.0.0", "-Dquarkus.http.port=8090"]

ENV SOURCE_COMMIT=${SOURCE_COMMIT} SOURCE_BRANCH=${SOURCE_BRANCH} IMAGE_NAME=${IMAGE_NAME}
