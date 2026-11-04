# The toolchain of date-rule-flex-bison: gcc, make, flex and bison. From the project root:
#   docker build -t date-rule-flex-bison date-rule-flex-bison
#   docker run --rm -u "$(id -u):$(id -g)" -v "$PWD:/work" -w /work/date-rule-flex-bison date-rule-flex-bison make test
FROM debian:13-slim
RUN apt-get update \
 && apt-get install -y --no-install-recommends gcc libc6-dev make flex bison \
 && rm -rf /var/lib/apt/lists/*
