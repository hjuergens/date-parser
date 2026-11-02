# Plan: intervals, series and selection in the schedule language

Status: proposal, nothing implemented. Sets (`> NEXT {MON, FRI}`) and ordinals (`> NEXT 3RD FRI`) are implemented in all
eleven modules already.

## Context
A comparison with another maturity rule language showed what the schedule language lacks. That language nests
*selectors* that turn a date into a period and a period back into a date: `wednesday(3) of month(3) of quarter(2) of sp`
is the third Wednesday of the third month of the quarter after spot's, and `next(5, … of every(quarter))` counts through a
series. Translated so far (section "MaturityRule" of `examples/schedule.txt`): plain shifts, `> EOM`, the IMM dates
through `SPOT > EOQ > IMM` and `> NEXT 5TH IMM`, the nth weekday through `T + 2M > BOM - 1D > NEXT 3RD WED`, and
`next(firstday of month(2 or 5) of every(year))` through `SPOT > NEXT {1 FEB, 1 MAY}`.

Each of these works, but some are tricks rather than the meaning written down. `> BOM - 1D > NEXT 3RD WED` says
"the third Wednesday after the day before the first of the month". The language has no value for "the month of a date",
and so it can't say "the third Wednesday *in* it" or "the last business day *in* it". What we want:

- `[…]` for an interval (a period of days) and a series, `{…}` for a set, as is common in mathematics;
- a period as a value, and selection from it;
- series as values, with selection mapped over each element.

## 1. Intervals and series with `[…]`
```
interval = "[" expr ":" expr "]" .              (* every day from the first to the second, both included *)
series   = "[" expr ":" period ":" expr "]" .   (* today's range (a : step : b) *)
```
- `[2026-01-01 : 2026-01-05]` is five dates. `[T : 1W : T + 1M]` is today's `(T : 1W : T + 1M)`.
- The endpoints must be single dates, as they are for today's range (an event with several dates is an error there).
- Proposal: the range moves from `(…)` to `[…]` in the spec, both generators (`scripts/catalog_cases.py`,
  `scripts/date-holidays/generate.mjs`) and the docs. `(…)` then becomes free for **grouping** (section 3), which selection
  needs. One release could accept both forms; I'd rather switch in one commit, as all the cases are generated or in the
  spec file.

## 2. Periods as values
Calendar periods of a date, as intervals:
```
date  = … | ("WEEK" | "MONTH" | "QUARTER" | "HALFYEAR" | "YEAR") "OF" expr .
```
- `MONTH OF T` is `[T > BOM : T > EOM]`, and `QUARTER OF SPOT + 1Q` is the quarter after spot's. A week runs from Monday
  to Sunday (ISO).
- The expression after `OF` extends as far as it can: `MONTH OF SPOT + 2M` is the month of `SPOT + 2M`.
- As a schedule it yields its days, as `[a : b]` does.

## 3. Selection with `IN`
```
date      = … | selection .
selection = ( ORDINAL | "LAST" ) target "IN" interval-value .
target    = WEEKDAY | "BD" | "IMM" | annual | EVENT | "{" target { "," target } "}" .
```
`interval-value` is an interval, a calendar period from section 2, or a series (see below).

| MaturityRule | Proposed |
|---|---|
| `wednesday(3) of month(3) of quarter(2) of sp` | `3RD WED IN MONTH OF SPOT + 1Q > EOQ` |
| `business(5) of month of today` | `5TH BD IN MONTH OF T` |
| `lastday of month(5) of sp` | `SPOT + 4M > EOM` (as now), or `LAST DAY IN MONTH OF SPOT + 4M` |
| `next(7, (monday or friday) of every(week))` | `T > NEXT 7TH {MON, FRI}` (as now) |
| `next(5, wednesday(3) of month(3) of every(quarter)) of sp` | `SPOT > NEXT 5TH IMM` (as now) |

- `LAST BD IN MONTH OF T` is the last business day of the month in the market: the same date as `T > EOM > PRECEDING`,
  but written as what it means. `LAST FRI IN MONTH OF T` generalises the annual `LAST FRI MAR` to any month.
- No 5th Wednesday in the period is an error, as `5 WED FEB` is now.
- **Grouping**: shifts after a selection belong to the expression after `OF`, as it extends as far as it can. So
  `3RD WED IN MONTH OF T + 1D` takes the month of `T + 1D`, and `(3RD WED IN MONTH OF T) + 1D` is the day after the
  Wednesday. This needs `(…)` as a date, which is why the range moves to `[…]`.
- A selection over a series maps over its elements: `3RD WED IN MONTH OF [T : 3M : T + 1Y]` is a list of dates. That
  is MaturityRule's `every`, and `> NEXT 5TH` stays the way to count.
- A possible new keyword `DAY` (`LAST DAY IN …`, `1ST DAY IN …`) is the period's first or last day.

## 4. Sets elsewhere
- `IF {MON, TUE}` beside `IF MON TUE`, so that both places use the same notation. `IF MON TUE` stays, as the catalog and
  date-holidays generators write it.
- A set as an item (`{1 FEB, 1 MAY}` as dates in T's year) is the same as a list (`1 FEB, 1 MAY`), so not needed.

## 5. The test prefix
The cases use `[<base> <market>]` at the start of a line, which an interval at the start of an expression would clash
with. All harnesses (eleven, with the same regex) narrow the prefix to an ISO date and an optional market name:
`\[(\d{4}-\d{2}-\d{2})(?: ([A-Z0-9]+))?\] `. An interval always contains a `:`, so `[2026-01-01 : 2026-01-31]` can't be
taken for the prefix. That keeps every existing case as it is.

## 6. Grammars: what to expect
- **LL(1)** (Coco/R): `MONTH OF expr` puts `expr` inside `date`, a recursion that is fine for LL. `[` starts an interval or
  a series; both read `expr ":"`, and the second `:` or `]` decides, after the common prefix.
- **LALR** (CUP, Bison, SableCC, Lark): after `OF expr`, a `+` either continues the inner expression or applies to the
  selection, a shift/reduce conflict. "Extends as far as it can" is the default shift. Bison and CUP resolve it with a
  precedence declaration; SableCC has none and needs the grammar split into `expr` and `inner_expr`.
- **PEG, combinators, instaparse**: the greedy reading is the natural one. instaparse must not report two parses, so
  the grammar must be unambiguous there too.
- `OF`, `IN`, `WEEK`, `MONTH`, `QUARTER`, `HALFYEAR`, `YEAR` and `DAY` become keywords. None of them is six letters
  long, so they don't clash with currency pairs. The keyword `MONTH` must not be confused with the token class `MONTH`
  of the month names; the grammars would call the keyword `MONTHOF` or similar.

## 7. Cases (about 40, `examples/schedule.txt`, sections "intervals and series" and "selection")
- Intervals: `[2026-01-30 : 2026-02-02]`, an empty interval (end before start: `none`), endpoints from events (error).
- Series: today's range cases, moved to `[…]`.
- Periods: `MONTH OF T`, `QUARTER OF T`, `WEEK OF T` (Monday to Sunday), `YEAR OF T`, `HALFYEAR OF T + 6M`.
- Selection: `3RD WED IN MONTH OF T`, `LAST FRI IN MONTH OF T`, `5TH BD IN MONTH OF T` with and without XNYS,
  `1ST {MON, FRI} IN WEEK OF T`, `5TH WED IN MONTH OF 2026-02-01` (error), grouping with and without `(…)`.
- MaturityRule: the quarter-based second IMM as `3RD WED IN MONTH OF SPOT + 1Q > EOQ`, the same as `SPOT > EOQ > IMM`;
  every IMM date of a year as a series.
- Every expected date is checked in Python before the tools run, as before.

## 8. Order
1. The prefix regex in the eleven harnesses, and the range moved to `[…]` (spec, generators, docs). A commit of its own,
   with all case counts unchanged.
2. Intervals, periods and selection: Lark first, then the shared semantics (Java, Python, Scala, Peggy), then the
   grammars; a full run on both machines.
3. README, module READMEs (one note per tool on how it resolved the grouping), page and playground.

## Open questions
- Should the range keep `(…)` as an alias for a while, or move in one commit (proposed)?
- Are the words `OF` and `IN` right, or would you prefer MaturityRule's style (`3RD WED OF MONTH OF …`)? `IN` keeps
  "select from" apart from "the period of".
- Closed intervals only, or also half-open `[a : b)`? Proposed: only closed ones. Writing `b - 1D` is clearer than a
  bracket that doesn't match.
