FROM maven:3.6.3-jdk-11-slim@sha256:18f059e73cffdf1688093e2e82f1001a7cd2baa9de92e7b8d05bf34a8318ee92 as maven

FROM adoptopenjdk:11.0.6_10-jdk-hotspot@sha256:4be6d6e43ffb3ebac9ef4f4c2ea61e374690dd8ac39014bc7ca84c3ae2f26b6e \
  as dev

COPY --from=maven /usr/share/maven /usr/share/maven
RUN ln -s /usr/share/maven/bin/mvn /usr/bin/mvn
ENV MAVEN_HOME=/usr/share/maven
ENV MAVEN_CONFIG=/root/.m2

WORKDIR /workspace
RUN mvn io.quarkus:quarkus-maven-plugin:1.3.0.Final:create \
    -DprojectGroupId=org.example.temp \
    -DprojectArtifactId=kafka-quickstart \
    -Dextensions="kafka" && \
    cd kafka-quickstart && \
    mkdir -p src/test/java/org && echo 'package org; public class T { @org.junit.jupiter.api.Test public void t() { } }' > src/test/java/org/T.java
COPY pom.xml kafka-quickstart/
RUN set -e; \
  (mvn -f kafka-quickstart/pom.xml package -Pnative || echo "# Build error is expected. Meant to prepare for native-image run."); \
  rm -r kafka-quickstart
COPY . .

ENTRYPOINT [ "mvn", "compile", "quarkus:dev" ]
CMD [ "-Dquarkus.http.host=0.0.0.0", "-Dquarkus.http.port=8090" ]

FROM openjdk:8-jdk-slim@sha256:71592a5c3eecf243b624f0a718402bb54d9ccb282b3ae3aa108f62b5cd5539d1 \
  as maven-build

COPY --from=maven /usr/share/maven /usr/share/maven
RUN ln -s /usr/share/maven/bin/mvn /usr/bin/mvn
ENV MAVEN_HOME=/usr/share/maven
ENV MAVEN_CONFIG=/root/.m2

COPY --from=dev ${MAVEN_HOME} ${MAVEN_HOME}
COPY --from=dev ${MAVEN_CONFIG} ${MAVEN_CONFIG}

WORKDIR /workspace
COPY . .

# Prepare everything, with a mock native-image executable
RUN set -e; \
  echo 'echo "native-image $@" | tee /workspace/native-image.sh' > /usr/local/bin/native-image; \
  chmod u+x /usr/local/bin/native-image
# Can't get integration tests to pass on docker hub, and can't get logs from them
# We're yet to try with memory capping flags though
#RUN mvn -o test
RUN set -e; \
  mvn -o -DskipTests package; \
  (mvn -o -DskipTests package -Pnative || echo "# Build error is expected. Meant to prepare for native-image run."); \
  stat target/kafka-keyvalue-1.0-SNAPSHOT-native-image-source-jar/lib; \
  stat target/kafka-keyvalue-1.0-SNAPSHOT-native-image-source-jar/kafka-keyvalue-1.0-SNAPSHOT-runner.jar; \
  cat native-image.sh | sed 's| | \\\n  |g'

FROM adoptopenjdk:11.0.6_10-jdk-hotspot@sha256:4be6d6e43ffb3ebac9ef4f4c2ea61e374690dd8ac39014bc7ca84c3ae2f26b6e \
  as runtime-jre
ARG SOURCE_COMMIT
ARG SOURCE_BRANCH
ARG IMAGE_NAME

# RUN apk add --no-cache snappy snappy lz4 zstd

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
FROM oracle/graalvm-ce:20.0.0-java11@sha256:ef4ea583db18977ffc0258efee96f02a003abd0e260e397d10bc0fbe86fdc18b \
  as native-build
RUN gu install native-image

# https://github.com/quarkusio/quarkus/issues/2718
#RUN yum -y install snappy libzstd lz4 && yum clean all

WORKDIR /project
COPY --from=maven-build /workspace/target/kafka-keyvalue-1.0-SNAPSHOT-native-image-source-jar /project/target/kafka-keyvalue-1.0-SNAPSHOT-native-image-source-jar

# Native image args are printed in the prepare step in maven-build above
RUN (cd target/kafka-keyvalue-1.0-SNAPSHOT-native-image-source-jar/ && \
  native-image \
  -J-Djava.util.logging.manager=org.jboss.logmanager.LogManager \
  -J-Dsun.nio.ch.maxUpdateArraySize=100 \
  -J-Dvertx.logger-delegate-factory-class-name=io.quarkus.vertx.core.runtime.VertxLogDelegateFactory \
  -J-Dvertx.disableDnsResolver=true \
  -J-Dio.netty.leakDetection.level=DISABLED \
  -J-Dio.netty.allocator.maxOrder=1 \
  -J-Duser.language=en \
  -J-Dfile.encoding=UTF-8 \
  --initialize-at-build-time= \
  #-H:InitialCollectionPolicy=com.oracle.svm.core.genscavenge.CollectionPolicy$BySpaceAndTime \
  -H:+JNI \
  -jar \
  kafka-keyvalue-1.0-SNAPSHOT-runner.jar \
  -H:FallbackThreshold=0 \
  -H:+ReportExceptionStackTraces \
  -H:-AddAllCharsets \
  -H:-IncludeAllTimeZones \
  -H:EnableURLProtocols=http \
  #-H:NativeLinkerOption=-no-pie \
  --no-server \
  -H:-UseServiceLoaderFeature \
  -H:+StackTrace \
  kafka-keyvalue-1.0-SNAPSHOT-runner \
  )

# The rest should be identical to src/main/docker/Dockerfile which is the recommended quarkus build
FROM registry.access.redhat.com/ubi8/ubi-minimal:8.1@sha256:01b8fb7b3ad16a575651a4e007e8f4d95b68f727b3a41fc57996be9a790dc4fa
ARG SOURCE_COMMIT
ARG SOURCE_BRANCH
ARG IMAGE_NAME

WORKDIR /work/
COPY --from=native-build /project/target/kafka-keyvalue-1.0-SNAPSHOT-native-image-source-jar/*-runner /work/application
#RUN chmod 775 /work
EXPOSE 8090
ENTRYPOINT ["./application", "-Djava.util.logging.manager=org.jboss.logmanager.LogManager"]
CMD ["-Dquarkus.http.host=0.0.0.0", "-Dquarkus.http.port=8090"]

ENV SOURCE_COMMIT=${SOURCE_COMMIT} SOURCE_BRANCH=${SOURCE_BRANCH} IMAGE_NAME=${IMAGE_NAME}
