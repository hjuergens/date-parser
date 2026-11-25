# date-rule-pest

The shared date schedule language parsed with [pest](https://pest.rs/) (Rust). pest generates a PEG parser from a
grammar file at compile time (`#[derive(Parser)]`): ordered choice, no lexer, and a parse tree of `Pair`s without
actions.

## What it parses

[schedule.pest](src/schedule.pest) is the [shared schedule example](../README.md#shared-example-schedule):

| Input | Result |
|---|---|
| `2015-06-09 + 3M, 09.06.2015` | `[2015-09-09, 2015-06-09]` |
| `[2015-01-15 : 1Q : 2015-12-31]` | `[2015-01-15, 2015-04-15, 2015-07-15, 2015-10-15]` |
| `2015-06-12 > SPOT USDCAD + 2B` | `[2015-06-17]` |
| `2015-06-09 + 3X` | `Error::Syntax` |

## Usage

```rust
use chrono::NaiveDate;
use pest_schedule::{parse, Ctx};

let rule = parse("2015-06-09 + 3M > IMM")?;
rule.eval(&Ctx::new(None))?;                                         // [2015-09-16]
let t = NaiveDate::from_ymd_opt(2015, 6, 9).unwrap();
parse("T + 1B")?.eval(&Ctx::new(Some(t)).with_market(market))?;    // market: any impl Market
```

## Build and test

```
mise run test:pest        # or, inside this directory:
cargo test
```

`cargo test` runs the 572 cases of [examples/schedule.txt](../examples/schedule.txt), the 2186 of
[examples/catalog.txt](../examples/catalog.txt) and the 3347 of [examples/date-holidays.txt](../examples/date-holidays.txt)
(one test per file), the 86 cases of [examples/rules.txt](../examples/rules.txt) for rule files, and 23 checks of the rule
grammar: 22 declarations, and that it accepts every case of the three files exactly when schedule.pest does.

## Shared example

- **No actions:** pest only parses. [tree.rs](src/tree.rs) builds the syntax tree of [ast.rs](src/ast.rs) from the
  parse tree, and [semantics.rs](src/semantics.rs) evaluates it, as SableCC's evaluator and the C module do. Errors the
  grammar can't see (an invalid date, an ordinal like `2ST`, a step that isn't positive) come from the builder.
- **Ordered choice and no lexer,** as in Peggy: `TOM` before `T`, `ADARII` before `ADAR`, and the pair pattern excludes
  the six-letter keywords (`!("EASTER" | "TISHRI" | …) ~ ASCII_ALPHA_UPPER{6}`). `>SPOTUSDCAD` without a space parses.
- **Whitespace is explicit.** With a `WHITESPACE` rule pest skips it between all tokens of non-atomic rules, also inside
  `2015-06-09`; the grammar has none and writes the silent rule `ws` where spaces may stand, as `_` in Peggy.
- **Two grammars, one builder:** pest generates one `Rule` enum per grammar, so the parse tree is first copied into
  `Node`s that carry the rule name as text. schedule.pest and rules.pest name their nodes alike, and one builder reads both.
- **Several dates from one expression** (events, series, `A & B`, `> {1 FEB, 1 MAY}`): such a node returns
  `Error::Unbound(dates)` until `values` binds it, and the item is evaluated once per date, as in the other modules.
- **Calendars:** the Umm al-Qura and Chinese months come from the tables of the C module
  ([lunar_tables.h](../date-rule-flex-bison/src/lunar_tables.h)), which [build.rs](build.rs) turns into Rust, so both
  read one copy; the Hebrew calendar and the sun (equinoxes, solstices, solar terms) are computed as there, IANA time
  zones come from [chrono-tz](https://crates.io/crates/chrono-tz).

## Rule files

[rules.pest](src/rules.pest) is the rule-file grammar of [rules.lark](../date-rule-lark/src/lark_schedule/rules.lark)
written as a PEG, after [rulefile.pegjs](../date-rule-peg/rulefile.pegjs) (keywords end at a word boundary, comments
start with `#`); [catalog.rs](src/catalog.rs) ports [catalog.py](../date-rule-lark/src/lark_schedule/catalog.py):

```rust
use pest_schedule::parse_rules;

let catalog = parse_rules(&text, announcements)?;     // announcements: HashMap<String, Vec<NaiveDate>>
catalog.holidays("country:JP", 2026)?;                 // Occurrence { date, rule, kind, projected, scenario, … }
catalog.evaluate("fx.spot", trade, None)?;             // a trade-date rule
catalog.schedule("T > SPOT EURUSD ON usd_eur")?.eval(&catalog.context(trade, None))?;
```

Scopes and their weekends, `ON` calendars with `ignore at T + 1 unless {…}`, the passes over rules that read the
holidays of their own scope, `ONE OF` alternatives, announcements and the load errors (cyclic or unknown references,
overlapping versions) work as described for [date-rule-lark](../date-rule-lark/README.md#rule-files). For the rule
files of [holidays-events](../holidays-events/) that load on their own, the holidays of every scope in 2025 to 2027 are
the same as Lark's.

## Status

Working.
