#FROM mailgun/kafka-pixy:0.16.0@sha256:2a2d426f9fe17ede9c21b2f8b7418d8787293e39327b638fd6c403f3726597fb
# https://github.com/solsson/kafka-pixy/commit/fec34113ad5701d93b0318bf00ab3626515d7d7b
FROM solsson/kafka-pixy:metadata-retries-conf@sha256:25b91e93d648a9539a5fb0888a5bcaa83e3ee1bb859764ae3e2a2f7f5dc5767c

COPY pixy.yaml /etc/pixy.yaml

ENTRYPOINT ["/usr/bin/kafka-pixy", "-config","/etc/pixy.yaml"]
