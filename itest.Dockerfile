FROM node:10.16.0-alpine@sha256:07897ec27318d8e43cfc6b1762e7a28ed01479ba4927aca0cdff53c1de9ea6fd \
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