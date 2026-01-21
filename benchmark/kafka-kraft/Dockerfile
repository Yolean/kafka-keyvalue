FROM solsson/kafka:2.8.0-nonroot@sha256:a4a6d0d6fe723ec838d3c26ccb402ec0356e4b7016193ef620b11daa2c789a35

COPY server.properties config/

COPY start-dev.sh .

ENTRYPOINT ["./start-dev.sh"]
