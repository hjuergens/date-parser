# Benchmark: the schedule language in C, from Go via SWIG, and with ANTLR's Python and Java targets

Results: [REPORT.md](REPORT.md). The site (`docs/`) doesn't use anything from this folder.

## What is measured

Each implementation does the same work, on all 5869 inputs of `examples/schedule.txt`, `catalog.txt` and `date-holidays.txt`:

- **Parse**: every input, syntax errors included.
- **Parse and evaluate**: read the spec files (their `market` and `event` declarations, so markets and holiday caches are
  fresh in every round), then parse and evaluate every case with its context.
- **Evaluate only** (Java): every case parsed once, then only evaluated in every round; the markets keep their
  holiday caches. This is where closures and compiled rules differ.

The implementations:

| Name | Program | What runs |
|---|---|---|
| C, native | [bench_c.c](bench_c.c) | `date-rule-flex-bison`: Flex scanner, Bison parser, C evaluator |
| Go → SWIG → C | [date-rule-flex-bison/go/cmd/bench](../../date-rule-flex-bison/go/cmd/bench/main.go) | the same C library, called through the SWIG binding |
| ANTLR 4, Python target | [bench_antlr.py](bench_antlr.py) | `date-rule-antlr-python`: ANTLR's Python runtime and `semantics.py` |
| ANTLR 4, Java target | [BenchJava.java](BenchJava.java) | `date-rule-antlr-java`: the rules as closures of `ScheduleSemantics` |
| ANTLR 4, Java, compiled to bytecode | `BenchJava compiled` | `date-rule-compiled`: the same parse, each rule a class of its own |

Protocol:
- one warm-up round, then 7 timed rounds of each kind, measured inside the process with a monotonic clock;
- three runs per machine, so the variation between runs shows;
- the machine must be idle first (load below 0.5);
- start-up is measured separately, as the median of five starts of `bench_* 0`. That mode parses and evaluates a
  single schedule, so it measures process start to first result.

Every program reports a checksum: the number of dates, the sum of their day numbers, the cases with an error and the
inputs that don't parse. The report says whether all checksums agree, which shows that the same work was done.

## Running it

[run.sh](run.sh) builds the programs and makes one run. It runs inside the dev container (with `mise install`),
from the project root:

```
HOST_OS="Linux (Ubuntu 26.04)" DOCKER_VERSION=29.9 benchmark/schedule/run.sh benchmark/schedule/results/<cpu>/run-1.json
python3 benchmark/schedule/report.py      # results/*/run-*.json -> REPORT.md
```

The folders under `results/` are named after the CPU. Machines are described only by their specs.
