FROM maven:3.6.1-jdk-8-slim@sha256:dce33cc7a4702cc5f3ea3a6deb4ea840c17001895ffe169d96e1fd9d7041eb15 as maven

FROM openjdk:11.0.3-jdk-slim@sha256:ee1ee5fd0c9cef0ec5ed72999567ed7a6efd5bfdbf49326bfd9423c0dca84ef0 \
  as dev

COPY --from=maven /usr/share/maven /usr/share/maven
RUN ln -s /usr/share/maven/bin/mvn /usr/bin/mvn
ENV MAVEN_HOME=/usr/share/maven
ENV MAVEN_CONFIG=/root/.m2

WORKDIR /workspace
RUN mvn io.quarkus:quarkus-maven-plugin:0.21.1:create \
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

FROM openjdk:11.0.3-jdk-slim@sha256:ee1ee5fd0c9cef0ec5ed72999567ed7a6efd5bfdbf49326bfd9423c0dca84ef0 \
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

FROM openjdk:11.0.3-jre-slim@sha256:73c29cc971f328f1456e443f55e4a7ce403638a0429a173549b5be76ef24ab37 \
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
FROM oracle/graalvm-ce:19.1.1@sha256:9c683cba2d1b40921509299cdec6f58412d245247fbb2ded38f1a4f3e3563fa5 \
  as native-build
RUN gu install native-image

# https://github.com/quarkusio/quarkus/issues/2718
#RUN yum -y install snappy libzstd lz4 && yum clean all

WORKDIR /project
COPY --from=maven-build /workspace/target/lib ./lib
COPY --from=maven-build /workspace/target/*-runner.jar ./

# from Quarkus' maven plugin mvn package -Pnative -Dnative-image.docker-build=true
# but CollectionPolicy commented out due to "Error: policy com.oracle.svm.core.genscavenge.CollectionPolicy cannot be instantiated."
RUN native-image \
  -J-Djava.util.logging.manager=org.jboss.logmanager.LogManager \
  --initialize-at-build-time= \
  -H:InitialCollectionPolicy=com.oracle.svm.core.genscavenge.CollectionPolicy$BySpaceAndTime \
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
