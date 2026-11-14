#!/usr/bin/env bash
# One benchmark run of the schedule language: the C library natively, from Go through SWIG, ANTLR's Python target
# and ANTLR's Java target, with closures and compiled to bytecode. Run from the project root inside the dev container (mise install done); writes one JSON
# object to $1.
#
#   benchmark/schedule/run.sh results/<cpu>/run-1.json
#
# HOST_OS and DOCKER_VERSION describe the machine outside the container; ROUNDS (default 7) is the number of timed
# rounds after the warm-up. See README.md for the protocol.
set -euo pipefail
out=$1
rounds=${ROUNDS:-7}
files="examples/schedule.txt examples/catalog.txt examples/date-holidays.txt"
work=$(mktemp -d)

make -s -C date-rule-flex-bison build/libschedule.a > /dev/null
cc -O2 -std=c11 -Idate-rule-flex-bison/src -Idate-rule-flex-bison/test -o "$work/bench_c" \
   benchmark/schedule/bench_c.c date-rule-flex-bison/test/spec.c date-rule-flex-bison/build/libschedule.a -lm
(cd date-rule-flex-bison/go && go build -o "$work/bench_go" ./cmd/bench)
mise run -q generate:antlr-python > /dev/null
(cd date-rule-antlr-python && uv sync -q)
python=date-rule-antlr-python/.venv/bin/python
export PYTHONPATH=date-rule-antlr-python/src   # the module isn't installed into its venv (uv: package = false)
mvn -q -pl date-rule-compiled -am install -DskipTests > /dev/null
mvn -q -pl date-rule-compiled dependency:build-classpath -Dmdep.outputFile="$work/classpath" > /dev/null
java_cp="date-rule-compiled/target/classes:$(cat "$work/classpath"):$work/java"
javac -d "$work/java" -cp "$java_cp" benchmark/schedule/BenchJava.java

# the median wall time in milliseconds of five starts: process start to the first result
startup() {
    for _ in 1 2 3 4 5; do
        start=$(date +%s%N); "$@" > /dev/null; echo $(( ($(date +%s%N) - start) / 1000 ))
    done | sort -n | sed -n 3p | awk '{ printf "%.1f", $1 / 1000 }'
}

c=$("$work/bench_c" "$rounds" $files)
go=$("$work/bench_go" "$rounds" $files)
py=$("$python" benchmark/schedule/bench_antlr.py "$rounds" $files)
java=$(java -cp "$java_cp" BenchJava "$rounds" $files)
compiled=$(java -cp "$java_cp" BenchJava compiled "$rounds" $files)
startup_c=$(startup "$work/bench_c" 0)
startup_go=$(startup "$work/bench_go" 0)
startup_py=$(startup "$python" benchmark/schedule/bench_antlr.py 0)
startup_java=$(startup java -cp "$java_cp" BenchJava 0)
startup_compiled=$(startup java -cp "$java_cp" BenchJava compiled 0)

mkdir -p "$(dirname "$out")"
"$python" - "$out" <<EOF
import json, platform, re, subprocess, sys
def first(command):
    return subprocess.run(command, shell=True, capture_output=True, text=True).stdout.strip().splitlines()[0]
cpu = re.search(r"model name\s*: (.*)", open("/proc/cpuinfo").read())   # missing on arm64: lscpu
cpu = cpu.group(1) if cpu else first("lscpu | sed -n 's/^Model name: *//p'")
memory = int(re.search(r"MemTotal:\s*(\d+)", open("/proc/meminfo").read()).group(1)) // 1024 ** 2
results = [json.loads(r) for r in [r'''$c''', r'''$go''', r'''$py''', r'''$java''', r'''$compiled''']]
startup = {"c": $startup_c, "go-swig": $startup_go, "antlr-python": $startup_py, "antlr-java": $startup_java,
           "compiled": $startup_compiled}
for r in results:
    r["startup_ms"] = startup[r["implementation"]]
json.dump({
    "commit": first("git rev-parse --short HEAD"),
    "rounds": $rounds,
    "machine": {"cpu": cpu, "threads": int(first("nproc")), "memory_gb": memory,
                "os": "${HOST_OS:-unknown}", "docker": "${DOCKER_VERSION:-unknown}"},
    "versions": {"cc": first("cc --version"), "go": first("go version"), "swig": first("swig -version | grep Version"),
                 "python": platform.python_version(),
                 "antlr4-runtime": first("$python -c 'import importlib.metadata as m; print(m.version(\"antlr4-python3-runtime\"))'"),
                 "java": first("java -version 2>&1")},
    "results": results,
}, open(sys.argv[1], "w"), indent=1)
EOF
rm -rf "$work"
echo "wrote $out"
