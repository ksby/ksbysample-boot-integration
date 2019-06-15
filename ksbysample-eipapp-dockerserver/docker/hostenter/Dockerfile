#
# MobyLinuxVM 接続用コンテナ作成用 Dockerfile
#
# コンテナ作成手順
#   1. コマンドプロンプトを起動して、カレントディレクトリを hostenter へ移動する
#   2. docker build . -t hostenter
#
# MobyLinuxVM 接続手順
#   1. docker run -it --privileged --pid=host hostenter /bin/sh
#
FROM alpine
MAINTAINER You <you@example.com>
RUN apk update && \
    apk add util-linux && \
    apk add iptables && \
    rm -rf /var/cache/apk/*
ENTRYPOINT ["nsenter", "--target", "1", "--mount", "--uts", "--ipc", "--net", "--pid"]
