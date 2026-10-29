"""Runs the shared cases of examples/schedule.txt with both Lark parsers."""
import re
from datetime import date
from pathlib import Path

import pytest
from lark.exceptions import UnexpectedInput

from lark_schedule import parse
from lark_schedule.semantics import WEEKENDS, Context, Market

CASES = Path(__file__).resolve().parents[2] / "examples" / "schedule.txt"
PREFIX = re.compile(r"\[(\S+)(?: (\S+))?\] (.*)")  # [base] or [base market]


def cases():
    markets = {}
    for line in CASES.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        if line.startswith("market "):  # market NAME = holiday, holiday, ...
            name, holidays = line.removeprefix("market ").split("=", 1)
            markets[name.strip()] = Market(date.fromisoformat(d.strip()) for d in holidays.split(","))
            continue
        text, expected = line.rsplit("=>", 1)
        text, context = text.strip(), Context()
        if prefix := PREFIX.fullmatch(text):
            base, market, text = prefix.groups()
            context = Context(date.fromisoformat(base), markets[market] if market else WEEKENDS)
        yield pytest.param(text, context, expected.strip(), id=line)


@pytest.mark.parametrize("parser", ["lalr", "earley"])
@pytest.mark.parametrize("text, context, expected", list(cases()))
def test_schedule(parser, text, context, expected):
    if expected == "error":
        with pytest.raises((UnexpectedInput, ValueError)):
            parse(text, parser)(context)
    else:
        dates = [] if expected == "none" else [date.fromisoformat(s.strip()) for s in expected.split(",")]
        assert parse(text, parser)(context) == dates
