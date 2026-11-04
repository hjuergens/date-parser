"""Benchmark of the ANTLR 4 Python target (date-rule-antlr-python): see README.md for the protocol shared with bench_c
and bench_go. Reads the spec files like that module's tests/test_schedule.py.

    PYTHONPATH=date-rule-antlr-python/src date-rule-antlr-python/.venv/bin/python benchmark/schedule/bench_antlr.py ROUNDS FILE...
    ... bench_antlr.py 0      start-up only: parses and evaluates one schedule and prints its date
"""
import json
import re
import sys
import time
from datetime import date

from antlr_schedule import parse
from antlr_schedule.semantics import WEEKENDS, Context, RuleMarket

PREFIX = re.compile(r"\[(\S+)(?: (\S+))?\] (.*)")  # [base] or [base market]
EPOCH = date(1970, 1, 1)


def load(path):
    """the cases of a spec file as (input, context or None if the market is missing); declarations first"""
    lines = open(path, encoding="utf-8").read().splitlines()
    markets, events, cases = {}, {}, []
    for line in lines:
        if line.startswith(("market ", "event ")):
            name, rule = line.split(" ", 1)[1].split("=", 1)
            try:
                rule = parse(rule.strip())
            except Exception:
                continue
            if line.startswith("market "):
                markets[name.strip()] = RuleMarket(rule)
            else:
                events[name.strip()] = rule
    for line in lines:
        if not line.strip() or line.startswith(("#", "market ", "event ")) or "=>" not in line:
            continue
        text = line.rsplit("=>", 1)[0].strip()
        context = Context(events=events)
        if prefix := PREFIX.fullmatch(text):
            base, market, text = prefix.groups()
            context = Context(date.fromisoformat(base), markets.get(market) if market else WEEKENDS, events)
        cases.append((text, context if context.market is not None else None))
    return cases


def parse_round(specs):
    errors = 0
    for cases in specs:
        for text, _ in cases:
            try:
                parse(text)
            except Exception:
                errors += 1
    return errors


def evaluate_round(files):
    checksum = {"dates": 0, "sum": 0, "errors": 0}
    for file in files:
        for text, context in load(file):
            try:
                if context is None:
                    raise ValueError("no market")
                dates = parse(text)(context)
            except Exception:
                checksum["errors"] += 1
                continue
            checksum["dates"] += len(dates)
            checksum["sum"] += sum((d - EPOCH).days for d in dates)
    return checksum


def measure(rounds, work):
    times = []
    for _ in range(rounds):
        start = time.perf_counter()
        work()
        times.append(time.perf_counter() - start)
    return times


def main():
    if sys.argv[1:] == ["0"]:
        print(parse("2015-06-09 + 3M")(Context())[0])
        return
    rounds, files = int(sys.argv[1]), sys.argv[2:]
    specs = [load(file) for file in files]
    parse_errors = parse_round(specs)   # warm-up
    parse_times = measure(rounds, lambda: parse_round(specs))
    checksum = evaluate_round(files)    # warm-up
    evaluate_times = measure(rounds, lambda: evaluate_round(files))
    checksum["parse_errors"] = parse_errors
    print(json.dumps({"implementation": "antlr-python", "cases": sum(len(c) for c in specs),
                      "parse": parse_times, "evaluate": evaluate_times, "checksum": checksum}))


if __name__ == "__main__":
    main()
