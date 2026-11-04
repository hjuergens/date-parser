# The toolchain of date-rule-flex-bison: gcc, make, flex and bison, and Go and SWIG for the binding in go/. From the project root:
#   docker build -t date-rule-flex-bison date-rule-flex-bison
#   docker run --rm -u "$(id -u):$(id -g)" -v "$PWD:/work" -w /work/date-rule-flex-bison date-rule-flex-bison sh -c 'make test && make test-go'
FROM debian:13-slim
RUN apt-get update \
 && apt-get install -y --no-install-recommends gcc libc6-dev make flex bison golang-go swig \
 && rm -rf /var/lib/apt/lists/*
# writable for any user id passed with -u; GOTOOLCHAIN=local keeps go from downloading another toolchain
ENV GOCACHE=/tmp/go-cache GOPATH=/tmp/go GOTOOLCHAIN=local
