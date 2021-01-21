FROM yolean/builder-quarkus:b06240e1543be9ce1800744b07e6a975e3031496@sha256:b82a1f82745834bb0d19acef72707a3adcf92811dd694a0671dae7102aec8466 \
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

RUN mvn --batch-mode $build

FROM yolean/java:b06240e1543be9ce1800744b07e6a975e3031496@sha256:4d61e888e9f4c58b4bfcbef45b2ecc3929b21b78ee01b1ae152a9c123ca2d3da \
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

FROM yolean/runtime-quarkus:b06240e1543be9ce1800744b07e6a975e3031496@sha256:c7e0c861e99058f8dccd948eed2bd228d8f54496565657fe6e3edcf107803627

COPY --from=dev /workspace/target/*-runner /usr/local/bin/quarkus

EXPOSE 8090
CMD ["-Dquarkus.http.host=0.0.0.0", "-Dquarkus.http.port=8090"]

ARG SOURCE_COMMIT
ARG SOURCE_BRANCH
ARG IMAGE_NAME

ENV SOURCE_COMMIT=${SOURCE_COMMIT} SOURCE_BRANCH=${SOURCE_BRANCH} IMAGE_NAME=${IMAGE_NAME}
