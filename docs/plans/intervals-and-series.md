# Plan: intervals, series and selection in the schedule language

Status: proposal, nothing implemented. Sets (`> NEXT {MON, FRI}`) and ordinals (`> NEXT 3RD FRI`) are implemented in all
eleven modules already. `> NEXT 3 FRI` stays an error: the number after `NEXT` only starts an annual date (`3 FRI JAN`),
and "the next three Fridays" would be a reading of its own.

## Context
A comparison with another maturity rule language showed what the schedule language lacks. That language nests
*selectors* that turn a date into a period and a period back into a date: `wednesday(3) of month(3) of quarter(2) of sp`
is the third Wednesday of the third month of the quarter after spot's, and `next(5, … of every(quarter))` counts through a
series. Translated so far (section "MaturityRule" of `examples/schedule.txt`): plain shifts, `> EOM`, the IMM dates
through `SPOT > EOQ > IMM` and `> NEXT 5TH IMM`, the nth weekday through `T + 2M > BOM - 1D > NEXT 3RD WED`, and
`next(firstday of month(2 or 5) of every(year))` through `SPOT > NEXT {1 FEB, 1 MAY}`.

Each of these works, but some are tricks rather than the meaning written down. `> BOM - 1D > NEXT 3RD WED` says
"the third Wednesday after the day before the first of the month". What we want:

- selection from the period of a date: "the 3rd Wednesday in its month", "the last business day in its month";
- `[…]` for intervals and series and `{…}` for sets, as is common in mathematics;
- shifts applied to every element of a series (MaturityRule's `every`).

A first draft had selection nested, `3RD WED IN MONTH OF SPOT + 1Q > EOQ`. It reads from right to left, while the
language reads from left to right, and it needed a rule for how far the expression after `OF` reaches, parentheses for
grouping, and an `expr` inside `date`, a shift/reduce conflict in the LALR tools. Selection as one more shift has none of
that.

## 1. Selection as a shift
```
shift     = … | ">" count select "IN" PERIOD .
count     = ORDINAL | "LAST" | ORDINAL "LAST" .     (* 3RD, LAST, 2ND LAST *)
select    = target | "{" target { "," target } "}" .
target    = WEEKDAY | "BD" | "IMM" | annual | EVENT .   (* as after NEXT; LAST WED MAY only in braces *)
PERIOD    = "WEEK" | "MONTH" | "QUARTER" | "HALFYEAR" | "YEAR" .
```
The shift works on the current date of the chain and selects within its period: `T > 3RD WED IN MONTH` is the third
Wednesday in the month of `T`. Shifts after it apply to the result, as they always do.

| MaturityRule | Today | With selection |
|---|---|---|
| `wednesday(3) of month(3) of quarter(2) of sp` | `SPOT > EOQ > IMM` | `SPOT + 1Q > EOQ > 3RD WED IN MONTH` |
| `wednesday(3) of month(3) of quarter(1) of sp` | `SPOT > BOQ - 1D > IMM` | `SPOT > EOQ > 3RD WED IN MONTH` |
| `business(5) of month` | `T > BOM - 1D + 5B` | `T > 5TH BD IN MONTH` |
| `lastday of month(5) of sp` | `SPOT + 4M > EOM` | (stays) |
| `(monday or friday)` in the week | – | `T > 1ST {MON, FRI} IN WEEK` |
| – (FX: second-to-last business day) | – | `T > 2ND LAST BD IN MONTH` |

Semantics:
- `> nTH x IN P`: from the day before the period starts, `> NEXT nTH x`; an error if that leaves the period.
  `> nTH LAST x IN P`: from the day after the period ends, `> PREV nTH x`; an error if that leaves the period.
  `LAST` alone is `1ST LAST`.
- The period of a date: `WEEK` Monday to Sunday (ISO), `MONTH`, `QUARTER`, `HALFYEAR` and `YEAR` as `> BOM`/`> EOM`,
  `> BOQ`/`> EOQ`, `> BOH`/`> EOH` and `> BOY`/`> EOY` already define them.
- `BD` counts the market's business days, so `T > LAST BD IN MONTH` is the same date as `T > EOM > PRECEDING`.
- A 5th Wednesday in a month with four is an error, as `5 WED FEB` is now. `> 2ND IMM IN YEAR` is the June IMM date,
  `> 1ST @FOMC IN QUARTER` the quarter's first FOMC decision.
- The targets are those of `NEXT`, so `X > 1ST 4 JUL IN YEAR` is 4 July in the year of X and `> 1ST {MON, 25 DEC} IN
  MONTH` mixes both kinds. One exception: an annual date that starts with `LAST` must be written in braces,
  `> 1ST {LAST MON MAY} IN YEAR`. Without them, `> LAST MON IN MONTH` and `> LAST MON MAY IN YEAR` start alike and only
  the third word tells the count `LAST` from the date `LAST MON MAY`, which an LL(1) parser can't wait for. In braces a
  `LAST` can only start a date, as there is no count inside a set.

Examples, checked in Python (T = 2026-11-02): `T > 5TH BD IN MONTH` is 2026-11-06, `T > 2ND LAST BD IN MONTH` 2026-11-27,
`T > 3RD WED IN MONTH + 1D` 2026-11-19; with T = 2027-03-22 (spot 2027-03-24, after the March IMM date)
`SPOT + 1Q > EOQ > 3RD WED IN MONTH` is 2027-06-16, the same as `SPOT > EOQ > IMM`.

Grammars: after `>` the tokens `ORDINAL` and `LAST` are new starts beside `IMM`, `SPOT`, `NEXT`, `PREV` and the rolls,
so LL(1) stays. `IN` and the period words close the shift; no conflicts are expected in the LALR tools.
New keywords: `IN`, `WEEK`, `MONTH`, `QUARTER`, `HALFYEAR`, `YEAR`. None is six letters long, so no clash with currency
pairs. The token class of the month names is called `MONTH` in most grammars; the keyword gets another token name there.

## 2. Intervals and series with `[…]`
```
item     = ( expr | series { shift } ) [ "IF" … ] .   (* extended in section 3 *)
series   = open expr ":" [ period ":" ] expr close .   (* [a : step : b] is today's range (a : step : b) *)
open     = "[" | "]" .                             (* "]" leaves the start out, ISO 80000-2 *)
close    = "]" | "[" .                             (* "[" leaves the end out *)
```
- The range moves from `(…)` to `[…]`, in one commit: the spec, both generators (`scripts/catalog_cases.py`,
  `scripts/date-holidays/generate.mjs`), the docs and the playground examples. No alias for `(…)`.
- `[2026-01-30 : 2026-02-02]` is four dates, every day as with a step of `1D`. `]a : b[` leaves out both endpoints,
  `[a : b[` and `]a : b]` one of them, with or without a step: `[T : 1W : T + 1M[` stops before `T + 1M`. The ISO
  notation is used because `(a : b)`, the usual one for an open interval, is today's closed range; after the move it is
  an error, so an old range can't silently change its meaning.
- The endpoints must be single dates, as now (an event with several dates is an error there).
- **Shifts after a series apply to each element**, the way `@FOMC + 1B` applies to each date of the event:
  `[2026-01-01 : 3M : 2026-12-31] > EOQ > 3RD WED IN MONTH` is the IMM dates of 2026 (2026-03-18, 2026-06-17,
  2026-09-16, 2026-12-16). That is MaturityRule's `every`. Duplicates are kept, as in a list
  (`[2026-01-01 : 1M : 2026-03-31] > EOQ` is 2026-03-31 three times).
- A series becomes an item, so it can stand in a list beside other items and take `IF`:
  `[2026-01-01 : 1W : 2026-01-31] IF MON, 25 DEC`.
- LL(1): after the first `:` a number starts either the step (`3M`) or the end date (`3 MAR`, `2026-03-31`). Coco/R reads
  the number first and decides on the token after it, as it already does for the date notations.

## 3. Merging with `+` and `-`
```
item     = ( expr | collection { shift | ("+" | "-") collection } ) [ "IF" … ] .
collection = series | "{" expr { "," expr } "}" .
```
- `+` is the union, `-` the difference; the result is sorted and has no duplicates. `,` stays the list, which keeps the
  order as written and the duplicates.
  `[2026-01-01 : 1M : 2026-12-31] + [2026-01-15 : 1M : 2026-12-15]` is both series in one;
  `[2026-01-01 : 2026-01-31] - {1 JAN, 6 JAN}` is January without the two holidays.
- The operand must start with `[`, `]` or `{`. So the token after `+` or `-` decides between a shift (`+ 1M`) and a
  merge (`+ [`), and `- 4 JUL` can't be confused with `- 4D`.
- Left to right as always: `[a…] + [b…] + 1D` merges first and then shifts every element by a day.
- `{1 JAN, 6 JAN}` is a set of dates, the dates of the items in T's year, sorted and without duplicates. It is the operand
  of a merge, and as an item of its own it is the same as the list `1 JAN, 6 JAN` except for the order.

## 4. Sets after IF
- `IF {MON, TUE}` beside `IF MON TUE`, so that both places can use the same notation. `IF MON TUE` stays, as the catalog
  and date-holidays generators write it.

## 5. Later and optional: picks and open-ended series
A *pick* takes one element of a series by its position. An open-ended series (`[SPOT : 1W : ]`, no end) is infinite and
can only be evaluated with a pick in front of the item:
```
7TH [SPOT : 1W : ] > 1ST {MON, FRI} IN WEEK      the 7th of these dates, MaturityRule's next(7, …)
5TH [SPOT : 1Q : ] > EOQ > 3RD WED IN MONTH      the 5th IMM date
```
- The pick counts the dates after the shifts, from the first one on or after the series' start; `LAST` and an open end
  is an error.
- For these two `> NEXT 7TH {MON, FRI}` and `> NEXT 5TH IMM` exist already. A pick adds something only for rules that
  `NEXT` can't search for, such as "the 3rd business day in a month", counted through the months. So it comes last, if
  at all.

## 6. The test prefix
The cases start with `[<base> <market>]`, which a series at the start of an expression would clash with. All eleven
harnesses narrow the prefix to an ISO date and an optional market name, `\[(\d{4}-\d{2}-\d{2})(?: ([A-Z0-9]+))?\] `.
A series always contains a `:`, so `[2026-01-01 : 2026-01-31]` can't be taken for the prefix, and every existing case
stays as it is.

## 7. Cases (about 60 in `examples/schedule.txt`, sections "selection" and "intervals and series")
- Selection: `> 3RD WED IN MONTH`, `> LAST FRI IN MONTH`, `> 5TH BD IN MONTH` with and without XNYS,
  `> 2ND LAST BD IN MONTH`, `> 1ST {MON, FRI} IN WEEK` (a week from Monday to Sunday), `> 2ND IMM IN YEAR`,
  `> 1ST @FOMC IN QUARTER`, `> LAST BD IN HALFYEAR`, `X > 1ST 4 JUL IN YEAR`, `> 1ST {MON, 25 DEC} IN MONTH`,
  `> 1ST {LAST MON MAY} IN YEAR`; errors: `> 5TH WED IN MONTH` in a month with four, `> 2ND 4 JUL IN YEAR` (only one),
  `> 0TH …`, `> LAST MON MAY IN YEAR` (`LAST …` without braces), `> 3RD WED IN` (no period), `> 3RD WED IN DAY`.
- The MaturityRule rows above, as cases in that section.
- Intervals and series: the existing range cases moved to `[…]`; `[a : b]`; an empty one (end before start: `none`);
  shifts after a series (the IMM dates of 2026, duplicates); a series in a list and with `IF`; `(…)` is now an error;
  `]a : b[`, `[a : b[`, `]a : b]`, with and without a step.
- Merging: `+` and `-` of series and sets, duplicates removed, sorted; shifts after a merge; `+ 4 JUL` (no bracket) is an
  error.
- Every expected date is checked in Python before the tools run, as before.

## 8. Order
1. The prefix regex in the eleven harnesses, and the range moved to `[…]` (spec, generators, docs, playground).
   A commit of its own; the case counts don't change.
2. Selection (section 1): Lark first, then the shared semantics (Java, Python, Scala, Peggy), then the grammars;
   a full run on both machines; a commit.
3. Series as items with shifts, open and half-open intervals, and `IF {…}` (sections 2 and 4); a full run; a commit.
4. Merging with `+` and `-`, and sets of dates (section 3); a full run; a commit.
5. Later and optional: picks and open-ended series (section 5).
6. With each commit: README, module READMEs (one note per tool if it needed a trick), page and playground.

## Decisions
- Counting from the end is written `2ND LAST`, as in English.
- `DAY` is no period, as a day has only one date to select (`> 1ST BD IN DAY` would only check T).
- The targets before `IN` are those of `NEXT`; `LAST …` dates only in braces.
- `]a : b[` and `[a : b[` for open and half-open intervals; `+` and `-` for merging; picks later, optional.
