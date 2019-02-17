FROM gradle:5.2.1-jdk11-slim@sha256:d76c7448ee9923493a9155c771a904ff6cedc3e3f4813416402debd125d21a48

USER root

# Installs docker like we do in https://github.com/Yolean/kafka-cache/
ENV docker_version=17.09.1~ce-0~debian

RUN export DEBIAN_FRONTEND=noninteractive \
  && apt-get update \
  && apt-get install -y apt-transport-https curl ca-certificates gnupg2 unzip \
  && curl -fsSL https://download.docker.com/linux/debian/gpg | apt-key add - \
  && apt-key fingerprint 0EBFCD88 \
  && echo "deb [arch=amd64] https://download.docker.com/linux/debian stretch stable" > /etc/apt/sources.list.d/docker.list \
  && apt-get update \
  && apt-get install -y --no-install-recommends docker-ce=$docker_version \
  && rm -r /var/lib/apt/lists/*

# This image expects a mounted docker.sock or env that points to docker tcp
RUN update-rc.d -f docker remove

USER gradle
# actually no, non-root typically won't be allowed to push to docker
# This might however mess up file rights in .gradle
USER root

WORKDIR /workspace
