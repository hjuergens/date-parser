# date-rule-antlr-python

The shared date schedule language parsed with [ANTLR 4](https://www.antlr.org/) (4.13.2), Python target.
It is the counterpart of [date-rule-antlr](../date-rule-antlr/README.md): the same tool and rules, another target language.

## What it parses

[DateSchedule.g4](DateSchedule.g4) has the rules of the Java module's `DateSchedule.g4`, but **no actions**:
Java code in a grammar can't be generated into Python. A visitor computes the dates instead.

| Input | Result |
|---|---|
| `2015-06-09 + 3M, 09.06.2015` | `[2015-09-09, 2015-06-09]` |
| `2015-06-12 > SPOT USDCAD + 2B` | `[2015-06-17]` |
| `2015-06-09 >SPOTUSDCAD` | `ScheduleSyntaxError` (the lexer reads `SPOTUS` as a pair) |

## Usage

```python
from datetime import date
from antlr_schedule import parse
from antlr_schedule.semantics import Context

parse("(T : 1B : T + 3B)")(Context(date(2015, 6, 11)))   # [2015-06-11, 2015-06-12, 2015-06-15, 2015-06-16]
```

## Build and test

```
mise run test:antlr-python      # generates the parser, then runs pytest
```

By hand, inside this directory:

```
uv run antlr4 -v 4.13.2 -Dlanguage=Python3 -visitor -no-listener -Xexact-output-dir -o src/antlr_schedule/generated DateSchedule.g4
uv run pytest
```

- `antlr4` comes from [antlr4-tools](https://pypi.org/project/antlr4-tools/): it downloads the ANTLR jar and runs it on Java,
  so generating a Python parser still needs a JDK (mise provides one). Without `java` on the `PATH` it asks to install a JRE.
- The runtime `antlr4-python3-runtime` must have exactly the tool's version (4.13.2).
- The generated sources in `src/antlr_schedule/generated` are not checked in.
- There are 2340 tests (pytest), one per case of [examples/schedule.txt](../examples/schedule.txt) and [examples/catalog.txt](../examples/catalog.txt).

## Shared example

- [`__init__.py`](src/antlr_schedule/__init__.py): `Rules(DateScheduleVisitor)` returns a function of the context from every visit;
  labeled alternatives (`# plus`, `# spot`, `# iso`, …) give one `visit…` method each.
- An error listener on lexer and parser raises `ScheduleSyntaxError`; the default one only prints and recovers.
- The Python target renames the rule `range` to `range_()`, because it would shadow the builtin.
- [semantics.py](src/antlr_schedule/semantics.py) is a copy of the Lark module's file (modules stay independent).
- Holiday rules and rolling: the date alternative `annual # inYear` and the anchor `annual # annualAnchor` share one rule; `MONTH`, `WEEKDAY` and `ROLL` are lexer rules. The keyword `'EASTER'` wins over the six-letter `PAIR`, as ANTLR defines implicit literals first.
- Lunar months and conditional dates: Lunar month names (`1 SHAWWAL`, `10 TISHRI`, `1 ZHENGYUE`) are more alternatives of `MONTH`, defined before `PAIR` like `'EASTER'`; `item : expr ('IF' WEEKDAY+)?` wraps each list item, and the visitor leaves out the items without a date.

## Status

Working.
