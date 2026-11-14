# A guide to the schedule language

This guide introduces the date language of this project step by step. The complete grammar is the EBNF in the
[README](README.md#shared-example-schedule); the spec file [examples/schedule.txt](examples/schedule.txt) holds 537
cases with their expected dates. All twelve implementations pass all of them, so none of them is a subset: any of
them can serve as a reference, and the ANTLR grammar [DateSchedule.g4](date-rule-antlr-java/src/main/antlr4/io/github/hjuergens/time/DateSchedule.g4)
is the one with the most comments.

## The idea in three sentences

A schedule is a **rule**, not a list of dates: it is evaluated later, with a **base date** `T` and a **market**.
The market only says which days are business days (Saturdays, Sundays and its holidays are not). The same rule gives
different dates for a different `T` or a different market.

All examples below are evaluated with `T` = Monday 2026-11-02 in the market XNYS (New York Stock Exchange holidays).

## Learning path

### Level 1: dates, periods, lists

| Input | Dates | |
|---|---|---|
| `2026-11-02`, `09.06.2015`, `06/09/2015` | 2026-11-02, 2015-06-09, 2015-06-09 | ISO, German, US notation |
| `T` | 2026-11-02 | the base date |
| `T + 3M` | 2027-02-02 | plus a period |
| `T + 1W - 2D` | 2026-11-07 | periods apply from left to right |
| `2026-01-31 + 1M` | 2026-02-28 | months first, the day clamped to the end of the month |
| `T + 2B` | 2026-11-04 | business days of the market |
| `T, 25 DEC` | 2026-11-02, 2026-12-25 | a list: one date per item |

### Level 2: shifts and selection

A shift starts with `>` and moves the date it follows.

| Input | Dates | |
|---|---|---|
| `TOM`, `SPOT`, `SPOT USDCAD` | 2026-11-03, 2026-11-04, 2026-11-03 | tomorrow (T + 1B), FX spot (T + 2B; T + 1B for USD/CAD) |
| `T > IMM` | 2026-12-16 | the next IMM date: 3rd Wednesday of Mar, Jun, Sep, Dec |
| `T > EOM` | 2026-11-30 | end of month; also `BOM`, `BOW`/`EOW`, `BOQ`/`EOQ`, `BOH`/`EOH`, `BOY`/`EOY` |
| `25 DEC > FOLLOWING` | 2026-12-28 | to a business day; also `PRECEDING`, `MODFOLLOWING` |
| `T > NEXT FRI`, `T > NEXT 3RD FRI` | 2026-11-06, 2026-11-20 | the first (third) match strictly after the date |
| `T > PREV 4 JUL` | 2026-07-04 | the last match strictly before |
| `T > 3RD WED IN MONTH` | 2026-11-18 | selection within the date's month (or `WEEK`, `QUARTER`, `HALFYEAR`, `YEAR`) |
| `SPOT > FIX`, `T + 2F` | 2026-11-02, 2026-11-04 | back from the value date to the fixing date (spot lag in fixing days); `F` counts fixing days |
| `T > LAST BD IN MONTH` | 2026-11-30 | counted from the end; `2ND LAST` is the second-to-last |

### Level 3: dates that recur every year

An annual date means the date in the year of `T`.

| Input | Dates | |
|---|---|---|
| `4 JUL`, `3 MON JAN`, `LAST MON MAY` | 2026-07-04, 2026-01-19, 2026-05-25 | day of month, n-th weekday, last weekday |
| `EASTER - 2D` | 2026-04-03 | Good Friday |
| `4 JUL {SAT - 1D, SUN + 1D}` | 2026-07-03 | an observance: the first entry for the date's weekday applies |
| `24 DEC IF MON TUE WED THU` | 2026-12-24 | the date only on these weekdays, otherwise no date |

Lunar months (`1 SHAWWAL`, `10 TISHRI`, `1 ZHENGYUE`), equinoxes and solar terms work the same way.

### Level 4: several dates from one item

| Input | Dates | |
|---|---|---|
| `[T : 1W : T + 3W]` | 2026-11-02, -09, -16, -23 | a series: start, step, end |
| `[T : T + 3D[` | 2026-11-02, -03, -04 | every day; `[` at the end leaves the end out, `]` at the start the start |
| `[T : 3M : T + 9M] > EOM` | 2026-11-30, 2027-02-28, 2027-05-31, 2027-08-31 | the shifts after a series apply to each element |
| `[T : T + 2D] & 25 DEC` | 2026-11-02, -03, -04, 2026-12-25 | include: sorted, without duplicates |
| `[T : T + 13D] \ {11 NOV}` | the 14 days without 2026-11-11 | exclude |
| `[T : 1Y : T + 1Y] > {1 FEB, 1 MAY}` | 2026-02-01, 2026-05-01, 2027-02-01, 2027-05-01 | dates in year: these annual dates in each element's year |
| `3RD [T : 1D : ] IF FRI` | 2026-11-20 | a pick: the 3rd date after `T`; `[T : 1D : ]` is open-ended |
| `@FOMC + 1B` | the day after each FOMC decision of 2026 | a named event, declared with the context |

## Walkthroughs

### `2015-06-09 + 3M, 09.06.2015`

The comma makes a list of two items. Each item is an expression: a date followed by shifts.

```
[exprs [item [expr [iso [NUM 2015] [NUM 06] [NUM 09]] [plus [period [NUM 3] [UNIT M]]]]]
       [item [expr [german [NUM 09] [NUM 06] [NUM 2015]]]]]
```

(its tree in Lark; paste it into a syntax tree generator, or type the input into the
playground of the [comparison page](https://hjuergens.github.io/date-parser/)). The first item is 2015-06-09 plus three
months, 2015-09-09; the second is a German date, 2015-06-09. Result: 2015-09-09, 2015-06-09, in the order written.

### `SPOT + 1Q > EOQ > 3RD WED IN MONTH`

```
[exprs [item [expr [spot_date ] [plus [period [NUM 1] [UNIT Q]]] [roll [ROLL EOQ]]
                   [select [from_start [ORDINAL 3RD]] [weekday [WEEKDAY WED]] [PERIOD MONTH]]]]]
```

One date, three shifts, each applied to the result of the one before:

| Step | Date |
|---|---|
| `SPOT`: T + 2 business days | 2026-11-04 |
| `+ 1Q`: plus three months | 2027-02-04 |
| `> EOQ`: last day of its quarter | 2027-03-31 |
| `> 3RD WED IN MONTH`: third Wednesday of March 2027 | 2027-03-17 |

This is MaturityRule's `wednesday(3) of month(3) of quarter(2) of sp`, the IMM date of the quarter after spot's.

### `[T : 3M : T + 9M] > EOM`

The series has four elements (2026-11-02, 2027-02-02, 2027-05-02, 2027-08-02). The shift after it applies to each of
them, so each moves to the end of its month: 2026-11-30, 2027-02-28, 2027-05-31, 2027-08-31.

## What applies to what

| Written after | Applies to | Example |
|---|---|---|
| a shift (`+ 1D`, `> EOM`, `> NEXT FRI`, …) | the date on its left; after a series, an event or `{…}` dates in year: each of their dates | `[T : 3M : T + 9M] > EOM` |
| `& B`, `\ B` | everything on its left; the shifts after `B` apply to the result | `[a : b] & c + 1D` shifts the union |
| `IF MON TUE`, `IF {SAT, SUN}` | each date of the item | `[T : T + 9D] IF {SAT, SUN}` |
| a pick (`5TH`, `2ND LAST`) | the whole item, which starts with a series or an event | `5TH [SPOT : 1Q : ] > EOQ > 3RD WED IN MONTH` |
| `,` | nothing: it separates items | `T, 25 DEC` |

- **`T`** is the base date; `TOM` and `SPOT` are computed from it in the market.
- **The fixing calendar** is a second market in the context (`F`, `FD`, `FIX`); without one, Monday to Friday.
- **An annual date** (`4 JUL`, `EASTER`, `1 SHAWWAL`) is the one in the year of `T`; after `NEXT`/`PREV` the nearest one.
- **`NEXT`/`PREV`** search strictly after or before, so `T > NEXT FRI` on a Friday is the following Friday.
- **A selection** (`> 3RD WED IN MONTH`) stays inside the period of the date and is an error if there is no such day.

## Glossary

| Term | Meaning | Example |
|---|---|---|
| `D`, `W`, `M`, `Q`, `H`, `Y` | day, week, month, quarter (3M), half year (6M), year | `T + 1Q` |
| `B` | business day of the market | `T + 2B` |
| `F` | fixing day: business day of the fixing calendar | `T + 2F` |
| `T`, `TOM`, `SPOT` | base date, T + 1B, FX spot (T + 2B, T + 1B for USD against CAD, TRY, PHP, RUB) | `SPOT USDCAD` |
| `IMM` | 3rd Wednesday of March, June, September, December | `T > IMM` |
| `BD` | a business day, as a target of `NEXT`/`PREV` or a selection | `T > NEXT BD` |
| `FD` | a fixing day, used like `BD` | `T > 2ND FD IN MONTH` |
| `> FIX [PAIR]` | the fixing date of a value date: minus the pair's spot lag in fixing days | `SPOT USDCAD > FIX USDCAD` |
| `>` | a shift that moves to a date (as opposed to `+`/`-` a period); "move to", not "next", so there is no `<`: backwards is `> PREV` | `T > EOM`, `T > PREV IMM` |
| `[a : step : b]` | a series; without a step every day; `]` at the start, `[` at the end leave that endpoint out | `[T : 1W : T + 3W]` |
| `{…}` after a date | an observance: weekday-dependent shift | `4 JUL {SAT - 1D}` |
| `{…}` after `>` | dates in year | `> {1 FEB, 1 MAY}` |
| `{…}` after `NEXT`, `IN` or `&`/`\` | a set: the nearest of several targets, or several dates | `T > NEXT {MON, FRI}` |
| `&`, `\` | include, exclude | `[a : b] \ {1 JAN}` |
| `1ST`, `2ND`, `3RD`, `LAST` | counts for `NEXT`, selections and picks | `T > NEXT 3RD FRI` |
| `@NAME` | a named event, its dates supplied with the context | `@FOMC + 1B` |

## Next

- The complete grammar: the EBNF in the [README](README.md#shared-example-schedule).
- Every rule with its expected dates: [examples/schedule.txt](examples/schedule.txt).
- The twelve implementations side by side and the playground: <https://hjuergens.github.io/date-parser/>.
- Changing rules while an application runs: [date-rule-runtime](date-rule-runtime/README.md).
