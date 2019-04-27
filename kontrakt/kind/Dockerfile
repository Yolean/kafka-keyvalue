FROM docker:18.09.5-dind@sha256:7ed03cb37cbe109867455393e670016149ae80fde931cf4773fabd8cf6284ee6 \
  as docker

FROM lachlanevenson/k8s-kubectl:v1.14.1@sha256:8267b932d262f0fc2c901f515d7752dd9e7c60c8410086e7a9fda935bcc3ba0d \
  as kubectl

FROM golang:1.12.4-alpine

COPY --from=docker /usr/local/bin/docker /usr/local/bin/docker
COPY --from=kubectl /usr/local/bin/kubectl /usr/local/bin/kubectl

ENV GO111MODULE=on
RUN set -e; \
  apk add --no-cache curl git; \
  go get sigs.k8s.io/kind@0.2.1

RUN git clone --branch kustomize https://github.com/Yolean/kubernetes-kafka

# Issue with sample scale-1 in
RUN set -e; \
  cd kubernetes-kafka; \
  sed -i 's/default.replication.factor=3/default.replication.factor=1/' kafka/10broker-config.yml; \
  sed -i 's/min.insync.replicas=2/min.insync.replicas=1/'               kafka/10broker-config.yml; \
  git --no-pager diff
