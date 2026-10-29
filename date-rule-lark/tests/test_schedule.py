"""Runs the shared cases of examples/schedule.txt with both Lark parsers."""
from datetime import date
from pathlib import Path

import pytest
from lark.exceptions import UnexpectedInput

from lark_schedule import parse

CASES = Path(__file__).resolve().parents[2] / "examples" / "schedule.txt"


def cases():
    for line in CASES.read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.startswith("#"):
            text, expected = line.rsplit("=>", 1)
            yield pytest.param(text.strip(), expected.strip(), id=line)


@pytest.mark.parametrize("parser", ["lalr", "earley"])
@pytest.mark.parametrize("text, expected", list(cases()))
def test_schedule(parser, text, expected):
    if expected == "error":
        with pytest.raises((UnexpectedInput, ValueError)):
            parse(text, parser)
    else:
        dates = [] if expected == "none" else [date.fromisoformat(s.strip()) for s in expected.split(",")]
        assert parse(text, parser) == dates
