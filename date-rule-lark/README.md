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

There are 12317 tests (pytest): the 572 cases of [examples/schedule.txt](../examples/schedule.txt), the 2186 of
[examples/catalog.txt](../examples/catalog.txt) and the 3347 of [examples/date-holidays.txt](../examples/date-holidays.txt), each with
`lalr` and `earley`, the 117 cases of [examples/rules.txt](../examples/rules.txt) for rule files, 23 for their grammar and 6
for what isn't dates (attributes, announcements, the API for hosts).

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
- Named events: the terminal `EVENT: /@[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)*/` is one more alternative of `date` and `anchor`; shifts receive the whole context, as `> NEXT @FOMC` needs the events.

## Rule files

[rules.lark](src/lark_schedule/rules.lark) puts the schedule language inside declarations
(`rule KEY for CLASS in SCOPES [except SCOPES] [valid from/to] = <schedule> [with …] [cites …];`, `weekend`, `calendar`),
as drafted for the rule catalog of holidays-events (its `docs/rule-grammar.md`, O-74), and
[catalog.py](src/lark_schedule/catalog.py) evaluates them ([rules.py](src/lark_schedule/rules.py) only builds the
declarations from the parse tree; date-rule-antlr-python copies catalog.py unchanged):

```python
from lark_schedule.rules import parse_rules

catalog = parse_rules(open("country.rules").read(), announcements={"ae.eid": [...]})   # a RuleCatalog
catalog.holidays("country:JP", 2026)     # Occurrence(date, rule, event_class, kind, precision, session, citations, projected)
catalog.evaluate("fx.spot", date(2026, 7, 2))                     # a trade-date rule
catalog.schedule("T > SPOT EURUSD ON usd_eur")(catalog.context(date(2026, 7, 2)))
```

- **Calendars:** a scope (`country:GB`, `region:AU-NSW`) has its weekend regime (`weekend country:AE = {SAT, SUN} from
  2022-01-01`) and the dates of its rules; `region:AU-NSW` also gets the rules of `country:AU` unless excluded. A
  business day or roll without `ON` uses the scope of the rule; `ON (a & b)` and declared calendars combine scopes,
  `ignore at T + 1 unless {MXN}` leaves a term out on the first business day after `T`. Kinds `closed` (default) and
  `non_working` close a day, `working_day` opens one, the others (`short_day`, …) don't change it.
- **Set-level passes:** rules that read the holidays of their own scope (`25 DEC {SAT SUN > FOLLOWING}`,
  `@national_holiday IF SUN > NEXT NONHOLIDAY`, `IF BETWEEN HOLIDAYS`) are evaluated in passes, in the order of their
  nominal dates (every day open), each seeing the current dates of the others, until nothing changes (at most 8 passes,
  else an error). This gives Christmas and Boxing Day in Britain and Japan's substitute holidays and days between
  holidays.
- **Load errors:** a cyclic or unknown reference, versions of a rule that overlap in time.
- **Not computed:** `HINDU` is an error at evaluation; `BY SIGHTING`, `DIYANET` and `FOLLOW` project with Umm al-Qura
  and mark the dates as projections.
- **Alternatives:** `ONE OF { item AS name, ... }` gives dates of which exactly one alternative
  happens; each date is a projection with `Occurrence.scenario` = its name, and together they are the rule's dates for
  references and calendars.
- **Rules only project. Announcements** are data, not grammar (`parse_rules(text, announcements={key: [dates]})`):
  the dates published under a rule's key replace the rule's dates for that year, and in the other years its dates are
  projections. A key without a rule holds announced dates only (an AGM, which no rule can project); other rules read
  it with `@key`, and a reference to a key that is neither a rule nor announced is a load error.
- **`with kind = event`** marks a dated event (a decision, a release, an auction) that leaves the day as it is, unlike
  `closed`, `non_working` and `working_day`.
- **[../holidays-events/](../holidays-events/)** holds the projection rules of holidays-events written in this language,
  with comments for readers without context, checked day by day against its rule engine.

The shared cases of [examples/rules.txt](../examples/rules.txt) ([test_rule_cases.py](tests/test_rule_cases.py)) check these
with real dates where they are known (Japan 2026, Britain 2021 and 2022, Chile, South Africa, Colombia, Argentina, Mexico, US
Inauguration Day, Orthodox Easter); [test_rules.py](tests/test_rules.py) checks the attributes, announcements and the API for
hosts, [test_rule_grammar.py](tests/test_rule_grammar.py)
the grammar: LALR(1) without conflicts, and its start rule `exprs` accepts every case of the three example files that
`schedule.lark` accepts, except references in lower case (`@za.freedom_day`).

## Status

Working.
