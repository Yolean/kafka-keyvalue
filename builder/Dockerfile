FROM solsson/jdk-opensource:11.0.1@sha256:740feb6c1ecbdf2beac1dc41405c3215511b90d83a7211f805e88f92946dd2a9

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

ENV GRADLE_VERSION=5.2.1

RUN cd /opt \
  && curl -o gradle-${GRADLE_VERSION}.zip -sLS https://downloads.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip \
  && unzip gradle-${GRADLE_VERSION}.zip \
  && rm gradle-${GRADLE_VERSION}.zip \
  && ln -s /opt/gradle-5.2.1/bin/gradle /usr/local/bin/gradle
