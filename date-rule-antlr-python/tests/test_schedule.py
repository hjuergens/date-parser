"""Runs the shared cases of examples/schedule.txt."""
from datetime import date
from pathlib import Path

import pytest

from antlr_schedule import ScheduleSyntaxError, parse

CASES = Path(__file__).resolve().parents[2] / "examples" / "schedule.txt"


def cases():
    for line in CASES.read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.startswith("#"):
            text, expected = line.rsplit("=>", 1)
            yield pytest.param(text.strip(), expected.strip(), id=line)


@pytest.mark.parametrize("text, expected", list(cases()))
def test_schedule(text, expected):
    if expected == "error":
        with pytest.raises((ScheduleSyntaxError, ValueError)):
            parse(text)
    else:
        dates = [] if expected == "none" else [date.fromisoformat(s.strip()) for s in expected.split(",")]
        assert parse(text) == dates
