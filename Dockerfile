FROM maven:3.6.2-jdk-8-slim@sha256:2b54b5981f55838fc4fba956e0092bc97b932ef011c7f70ca85caec337711741 as maven

FROM adoptopenjdk/openjdk11:jdk-11.0.4_11-slim@sha256:79f43f49f505df27528a3dce52e30339116ed6716b1f658206ba76caca26c85b \
  as dev

COPY --from=maven /usr/share/maven /usr/share/maven
RUN ln -s /usr/share/maven/bin/mvn /usr/bin/mvn
ENV MAVEN_HOME=/usr/share/maven
ENV MAVEN_CONFIG=/root/.m2

WORKDIR /workspace
RUN mvn io.quarkus:quarkus-maven-plugin:0.26.0:create \
    -DprojectGroupId=org.acme \
    -DprojectArtifactId=getting-started \
    -DclassName="org.acme.quickstart.GreetingResource" \
    -Dpath="/hello" && \
    rm -r src/test/java/org/acme && echo 'package org; public class T { @org.junit.jupiter.api.Test public void t() { } }' > src/test/java/org/T.java
COPY pom.xml .
RUN mvn package && \
  rm -r src target mvnw* && \
  ls -la
COPY . .

ENTRYPOINT [ "mvn", "compile", "quarkus:dev" ]
CMD [ "-Dquarkus.http.host=0.0.0.0", "-Dquarkus.http.port=8090" ]

FROM adoptopenjdk/openjdk11:jdk-11.0.4_11-slim@sha256:79f43f49f505df27528a3dce52e30339116ed6716b1f658206ba76caca26c85b \
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

FROM adoptopenjdk/openjdk11:jre-11.0.4_11@sha256:140ba182d696180600a2871a98f67ad0ee2d6a1e48a7c570d1c0e156860c2a9d \
  as runtime-plainjava
ARG SOURCE_COMMIT
ARG SOURCE_BRANCH
ARG IMAGE_NAME

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
FROM oracle/graalvm-ce:19.2.0@sha256:85c7d15f3797fd121d4a4adb0edae859311f6f789c80859fefee69eeee5ac386 \
  as native-build
RUN gu install native-image

# https://github.com/quarkusio/quarkus/issues/2718
#RUN yum -y install snappy libzstd lz4 && yum clean all

WORKDIR /project
COPY --from=maven-build /workspace/target/lib ./lib
COPY --from=maven-build /workspace/target/*-runner.jar ./

# from Quarkus' maven plugin mvn package -Pnative -Dnative-image.docker-build=true
RUN native-image \
  -J-Djava.util.logging.manager=org.jboss.logmanager.LogManager \
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
  -H:-SpawnIsolates \
  -H:+JNI \
  --no-server \
  -H:-UseServiceLoaderFeature \
  -H:+StackTrace

# The rest should be identical to src/main/docker/Dockerfile which is the recommended quarkus build
FROM registry.access.redhat.com/ubi8/ubi-minimal@sha256:2cfe133279640b2afbf5af3c87f246ca7aeeee16edc9d3ec187b35c929d84ba7
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
