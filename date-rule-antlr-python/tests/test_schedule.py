"""Runs the shared cases of examples/schedule.txt and examples/catalog.txt."""
import re
from datetime import date
from pathlib import Path

import pytest

from antlr_schedule import ScheduleSyntaxError, parse
from antlr_schedule.semantics import WEEKENDS, Context, RuleMarket

EXAMPLES = Path(__file__).resolve().parents[2] / "examples"
PREFIX = re.compile(r"\[(\S+)(?: (\S+))?\] (.*)")  # [base] or [base market]


def cases(file):
    markets, events = {}, {}
    for line in (EXAMPLES / file).read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        if line.startswith("market "):  # market NAME = schedule of the holidays
            name, holidays = line.removeprefix("market ").split("=", 1)
            markets[name.strip()] = RuleMarket(parse(holidays.strip()))
            continue
        if line.startswith("event "):  # event NAME = schedule of its dates
            name, dates = line.removeprefix("event ").split("=", 1)
            events[name.strip()] = parse(dates.strip())
            continue
        text, expected = line.rsplit("=>", 1)
        text, context = text.strip(), Context(events=events)
        if prefix := PREFIX.fullmatch(text):
            base, market, text = prefix.groups()
            context = Context(date.fromisoformat(base), markets[market] if market else WEEKENDS, events)
        yield pytest.param(text, context, expected.strip(), id=line)


@pytest.mark.parametrize("text, context, expected", [*cases("schedule.txt"), *cases("catalog.txt")])
def test_schedule(text, context, expected):
    if expected == "error":
        with pytest.raises((ScheduleSyntaxError, ValueError)):
            parse(text)(context)
    else:
        dates = [] if expected == "none" else [date.fromisoformat(s.strip()) for s in expected.split(",")]
        assert parse(text)(context) == dates
