FROM node:10.15.1-stretch-slim@sha256:18f831507f1c6953563105b973ee4db037b1d94f35985a984378e31a522e4cfa

RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*

WORKDIR /usr/src/app

COPY package* ./

RUN npm ci

COPY . .

ENTRYPOINT [ "./node_modules/.bin/jest" ]
