"""Runs the shared cases of examples/rules.txt: blocks of declarations, each followed by its cases."""
import re
from datetime import date
from pathlib import Path

import pytest

from antlr_schedule.rules import parse_rules

EXAMPLES = Path(__file__).resolve().parents[2] / "examples"
SCHEDULE = re.compile(r"\[(\d{4}-\d{2}-\d{2})\] (.*)")      # [base] schedule
SCOPE = re.compile(r"(\S+) (\d{4})")                         # scope year


def blocks(file="rules.txt"):
    """Yields (declarations, [(case text, expected, line)]) in the order of the file."""
    declarations, cases = [], []
    for line in (EXAMPLES / file).read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        if "=>" in line:
            text, expected = line.rsplit("=>", 1)
            cases.append((text.strip(), expected.strip(), line))
            continue
        if cases:
            yield "\n".join(declarations), cases
            declarations, cases = [], []
        declarations.append(line)
    if cases:
        yield "\n".join(declarations), cases


def evaluate(declarations, text):
    book = parse_rules(declarations)
    if m := SCHEDULE.fullmatch(text):
        base, schedule = m.groups()
        return [d.isoformat() for d in book.schedule(schedule)(book.context(date.fromisoformat(base)))]
    scope, year = SCOPE.fullmatch(text).groups()
    return [o.date.isoformat() + (f" {o.scenario}" if o.scenario else "") for o in book.holidays(scope, int(year))]


def result(declarations, text):
    try:
        return ", ".join(evaluate(declarations, text)) or "none"
    except Exception:
        return "error"


@pytest.mark.parametrize("declarations, text, expected",
                         [pytest.param(d, t, e, id=line) for d, cases in blocks() for t, e, line in cases])
def test_rule_case(declarations, text, expected):
    assert result(declarations, text) == expected
