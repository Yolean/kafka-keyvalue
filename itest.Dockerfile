FROM node:10.16.3-alpine@sha256:abd8fa1df6dc74213878a96d9c38601ffbb9deb80b0030e758a690699022d639 \
  as prepare

WORKDIR /usr/src/app

COPY package*.json ./

RUN npm install

COPY ./ ./

RUN ls -l

ENTRYPOINT [ "tail", "-f", "/dev/null" ]

FROM prepare as watch

ENTRYPOINT [ "./node_modules/.bin/jest", "--testMatch", "**/*.itest.ts", "--watchAll" ]

FROM prepare

ENTRYPOINT [ "./node_modules/.bin/jest", "--testMatch", "**/*.itest.ts" ]