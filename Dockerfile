FROM yolean/builder-quarkus:f63772d02556021dbcb9f49fb9eff3d3dbe1b636@sha256:6817137412415bc62dea3869824c424abc9c0246e3ac684b7454d4d29ba0b946 \
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

RUN mvn --batch-mode $build | tee build.log; \
  set -ex; \
  grep '[INFO] BUILD SUCCESS' build.log || \
    grep 'Native memory allocation (mmap) failed\|Exit code was 137 which indicates an out of memory error' build.log && \
    grep --color=never 'NativeImageBuildStep] /opt/graalvm' build.log | cut -d ' ' -f 3- | \
      (cd target/*-source-jar; sh - ); \
  rm build.log

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

FROM yolean/runtime-quarkus:f63772d02556021dbcb9f49fb9eff3d3dbe1b636@sha256:5619b52835239a57ab324500f8d17bc935c4e38e9f0f1a5d28348955df0a33b0

COPY --from=dev \
  /lib/x86_64-linux-gnu/libz.so.* \
  /lib/x86_64-linux-gnu/

COPY --from=dev \
  /usr/lib/x86_64-linux-gnu/libzstd.so.* \
  /usr/lib/x86_64-linux-gnu/libsnappy.so.* \
  /usr/lib/x86_64-linux-gnu/liblz4.so.* \
  /usr/lib/x86_64-linux-gnu/

COPY --from=dev /workspace/target/*-runner /usr/local/bin/quarkus

EXPOSE 8090
CMD ["-Dquarkus.http.host=0.0.0.0", "-Dquarkus.http.port=8090"]

ARG SOURCE_COMMIT
ARG SOURCE_BRANCH
ARG IMAGE_NAME

ENV SOURCE_COMMIT=${SOURCE_COMMIT} SOURCE_BRANCH=${SOURCE_BRANCH} IMAGE_NAME=${IMAGE_NAME}
