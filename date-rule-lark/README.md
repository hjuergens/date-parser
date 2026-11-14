# date-rule-lark

The shared date schedule language parsed with [Lark](https://github.com/lark-parser/lark) (Python).
Lark takes one grammar and builds either an **Earley** parser (any context-free grammar, also ambiguous ones)
or an **LALR(1)** parser from it. This module runs every shared test case with both.

## What it parses

[schedule.lark](src/lark_schedule/schedule.lark) is the [shared schedule example](../README.md#shared-example-schedule):

| Input | Result |
|---|---|
| `2015-06-09 + 3M, 09.06.2015` | `[2015-09-09, 2015-06-09]` |
| `[2015-01-15 : 1Q : 2015-12-31]` | `[2015-01-15, 2015-04-15, 2015-07-15, 2015-10-15]` |
| `2015-06-12 > SPOT USDCAD + 2B` | `[2015-06-17]` |
| `2015-06-09 + 3X` | `lark.exceptions.UnexpectedCharacters` |

## Usage

```python
from datetime import date
from lark_schedule import parse
from lark_schedule.semantics import Context, Market

parse("2015-06-09 + 3M > IMM")(Context())               # [datetime.date(2015, 9, 16)]
parse("2015-06-09 + 3M > IMM", "earley")(Context())     # same result, other algorithm
parse("T + 1B")(Context(date(2015, 6, 9), Market([date(2015, 6, 10)])))   # [datetime.date(2015, 6, 11)]
```

## Build and test

```
mise run test:lark        # or, inside this directory:
uv run pytest             # uv installs Lark and pytest into .venv (versions in uv.lock)
```

There are 12163 tests (pytest): the 537 cases of [examples/schedule.txt](../examples/schedule.txt), the 2186 of
[examples/catalog.txt](../examples/catalog.txt) and the 3347 of [examples/date-holidays.txt](../examples/date-holidays.txt), each with
`lalr` and `earley`, and 23 for the grammar of rule files ([test_rule_grammar.py](tests/test_rule_grammar.py)).

## Shared example

- A `Transformer` ([`__init__.py`](src/lark_schedule/__init__.py)) builds the rule bottom-up as closures over the context; each rule alias
  (`-> plus`, `-> spot`, `-> iso`, …) becomes one method. Exceptions raised in it arrive wrapped in `VisitError`,
  so `parse` unwraps them.
- [semantics.py](src/lark_schedule/semantics.py) ports `ScheduleSemantics.java` with the standard library only:
  `java.time`'s month arithmetic (day clamped to the end of the month), business days, IMM and spot dates.
- In LALR mode Lark uses a *contextual* lexer: it only tries the tokens the parser can accept next.
  After `>` these are `IMM` and `SPOT`, so `>SPOTUSDCAD` without a space parses, as in the scannerless PEG and Scala modules.
  ANTLR's lexer doesn't know the parser state, reads `SPOTUS` as a currency pair and rejects the input.
- The schedule language is unambiguous, so Earley shows no advantage here; an ambiguous notation such as
  `01/02/2015` (US or European order) would.
- Holiday rules and rolling: `MONTH`, `WEEKDAY` and `ROLL` are terminals. The contextual lexer would read `EASTER` after `SPOT` as
  a currency pair, because only a pair can follow there, so the pair pattern is `/(?!EASTER)[A-Z]{6}/`.
- Lunar months and conditional dates: Lunar month names (`1 SHAWWAL`, `10 TISHRI`, `1 ZHENGYUE`) are more alternatives of `MONTH`, and the pair pattern excludes the six-letter
  ones (`TISHRI`, …) as well. `item: expr ("IF" WEEKDAY+)?` keeps a date only on these weekdays.
- Ordinals and sets (`> NEXT 3RD FRI`, `> NEXT {MON, FRI}`): the terminal `ORDINAL.2: /[0-9]+(ST|ND|RD|TH)/` has a higher priority than `NUM`, as Lark's lexer doesn't take the longest match among regexes; `">" "NEXT" [ORDINAL] anchor` and `?anchor: target | "{" target ("," target)* "}"` run as LALR(1) without conflicts.
- Named events: the terminal `EVENT: /@[A-Z][A-Z0-9_]*/` is one more alternative of `date` and `anchor`; shifts receive the whole context, as `> NEXT @FOMC` needs the events.

## Rule files

[rules.lark](src/lark_schedule/rules.lark) is a grammar only, without a transformer: the schedule language inside
declarations (`rule KEY for KEY in SCOPE … = <schedule> with … cites …;`, `weekend`, `calendar`), drafted for the rule
catalog of holidays-events (its `docs/rule-grammar.md`, O-73). It adds named calendars (`+ 2B ON country:BR`), several
weekdays per observance entry (`{TUE WED > PREV MON}`), conditions on other dates and the holiday set
(`IF 18 SEP IS TUE`, `IF BETWEEN HOLIDAYS AND NOT HOLIDAY`, `IF EVERY 4Y FROM 1965`), `ORTHODOX EASTER`,
`ANNOUNCED BY … ELSE …` and `> POSTPONE … MAX 14D`. Lark builds it as LALR(1) without conflicts, and its start rule
`exprs` accepts every case of the three example files that `schedule.lark` accepts and rejects the others, except
references in lower case (`@za.freedom_day`), which rule files allow.

## Status

Working.
