# Plan: holiday rules in the schedule language

Status: Gregorian rules and rolling are implemented in all ten modules (§1, §1b, §2–4, §6); lunar calendars (§5) are open.

## Context
The holidays-events catalog (`../holidays-events/rules/catalog/us.yaml` and siblings) describes market closures
as typed rules: `fixed_gregorian`, `nth_weekday`, `last_weekday`, `easter_offset`, `observance_shift`,
`relative_to_event`, `last_business_day`, `nth_weekday_prev_business`, and lunar types (`hijri_fixed`,
`chinese_lunisolar`, `hebrew`). The user wants the date-parser grammar to express these rules. Decided:
- **Extend the shared schedule language** (no separate grammar), so holiday rules compose with `+ 1D`, `B`, `> SPOT`.
- **All ten implementations**, against the shared spec `examples/schedule.txt`.
- **Gregorian rule types now; lunar calendars are planned for but not implemented** (syntax kept open, TODO entry).
- This fits the existing rule model (function of base date and market): a year-anchored date takes **the year of the
  base date `T`**. With a holiday list, a market can now be *declared from rules*.

## 1. Language additions
```
date  = "T" | NUM ( "-" NUM "-" NUM | "." NUM "." NUM | "/" NUM "/" NUM     (existing)
                  | MONTH                  (* 4 JUL: day of month, year of T *)
                  | WEEKDAY MONTH )        (* 3 MON JAN: n-th weekday, n = 1..5 *)
      | "LAST" WEEKDAY MONTH               (* LAST MON MAY *)
      | "EASTER"                           (* Western Easter Sunday of T's year *)
expr  = date { "+" period | "-" period | ">" ( "IMM" | "SPOT" [PAIR] | "FOLLOWING" | "PRECEDING" )
             | "{" WEEKDAY ("+"|"-") period { "," WEEKDAY ("+"|"-") period } "}" }   (* observance *)
MONTH = JAN … DEC     WEEKDAY = MON … SUN
```
- **Observance `{SAT - 1D, SUN + 1D}`** mirrors the catalog's `shifts: {6: -1, 7: 1}`. It is one step that looks at the
  weekday of the incoming date, so items don't chain. Within braces the comma can't be confused with the list comma.
- **`> FOLLOWING` / `> PRECEDING`** are the ISDA business-day conventions. They are the identity on a business day,
  otherwise they move to the next or previous business day of the market.
- **Catalog mapping** (these become spec cases and README examples):
  | catalog | language |
  |---|---|
  | fixed_gregorian 7/4 | `4 JUL` |
  | nth_weekday 1/MON/3 | `3 MON JAN` |
  | last_weekday 5/MON | `LAST MON MAY` |
  | easter_offset -2 | `EASTER - 2D` |
  | observance_shift {6:-1,7:1} | `4 JUL {SAT - 1D, SUN + 1D}` |
  | relative_to_event thanksgiving +1 | `4 THU NOV + 1D` (the rule is written inline; named references are out of scope) |
  | nth_weekday_prev_business | `3 FRI MAR > PRECEDING` |
  | last_business_day FEB | `1 MAR - 1B` |
  | monthly_day_next_business | `20 JAN > FOLLOWING` (one month; per-month adjustment inside a range is out of scope) |
- Stays catalog metadata, not grammar: `valid_from`/`valid_to`, `session_close`, `scope`, `source`.
- **Errors:** `T` missing → error, as today. Day/n out of range (`32 JUL`, `0 MON JAN`, `6 MON JAN`) is rejected at
  parse time. Combinations that depend on the year (`29 FEB` in 2025, `5 MON FEB` in 2025) fail at evaluation.
  The harnesses already treat both as `error`.
- **Lexer pitfall:** `EASTER` is six capitals, which is also a `PAIR`. Keywords must win ties: ANTLR implicit literals,
  JFlex/SableCC rule order, the Lark keyword priority, the Coco keyword table, the Bison word scanner, and the PEG/Scala
  ordered choice. Each tool's fix is worth a lesson on the page (it sits next to the `SPOTUSDCAD` case).

## 1b. Rolling: dates relative to the base date
Year-anchored dates (`4 JUL` = in T's year) describe holiday calendars. Trading needs *rolling* dates seen from
today: next X, tomorrow, spot, next business day, begin and end of month. Additions:
```
date  = … | "TOM" | "SPOT" [PAIR]           (* TOM = T + 1B, SPOT = T > SPOT: money-market tenor names *)
expr  = date { …
             | ">" "NEXT" anchor | ">" "PREV" anchor        (* strictly after / before, like > IMM *)
             | ">" ( "BOM" | "EOM" | "BOY" | "EOY" )         (* first/last calendar day of month/year *)
             | ">" "MODFOLLOWING" }                          (* FOLLOWING, but PRECEDING if it leaves the month *)
anchor = WEEKDAY | "BD" | NUM MONTH | NUM WEEKDAY MONTH | "LAST" WEEKDAY MONTH | "EASTER" | "IMM"
```
- Examples:
  - `T > NEXT FRI`, `T > NEXT BD` (= `T + 1B`).
  - `T > NEXT 4 JUL`, `T > NEXT 3 MON JAN`, `T > NEXT EASTER - 2D`.
  - `T > EOM > PRECEDING` (last business day of the month), `T > BOM > FOLLOWING`.
  - `SPOT + 1M > MODFOLLOWING`, `TOM`, `SPOT EURUSD`.
- `NEXT` and `PREV` are strict, as `> IMM` is today. `> IMM` stays and equals `> NEXT IMM`. To include the date itself,
  write `- 1D > NEXT …`.
- `NEXT 4 JUL` evaluates the anchor in the date's year, and in the following year if the result isn't after the
  date. Observance written after the anchor applies to the result: `T > NEXT 4 JUL {SAT - 1D, SUN + 1D}`.
- Rolling also settles the lunar open point (§5): `T > NEXT 1 SHAWWAL` always has one result.
- `TOM`/`SPOT` as dates need a base date, as `T` does. `ON`/`TN` are periods between two dates, not dates, so they stay out.
- Spec: about 18 more cases in a section "rolling". They cover month-end edge cases (`2016-01-31 + 1M > EOM`,
  `[2025-05-30 XNYS] T + 1M > MODFOLLOWING => 2025-06-30`, a MODFOLLOWING that has to roll back) and a NEXT across
  the turn of the year (`[2025-12-01] T > NEXT 3 MON JAN => 2026-01-19`).
- Deferred to TODO: the end-of-month tenor rule (spot on the last business day of a month gives maturities on the last
  business day too) and IMM variants (CDS dates, the 20th).

## 2. Semantics (pattern repeats per language)
Add these to `ScheduleSemantics.java` (6 identical JVM copies, e.g.
`date-rule-antlr/src/main/java/io/github/hjuergens/time/ScheduleSemantics.java`), to the companion object in
`ScheduleParsers.scala`, to the initializer of `schedule.pegjs`, and to both copies of `semantics.py`:
- `monthDay(int day, int month)`, `nthWeekday(int n, DayOfWeek, int month)`, `lastWeekday(DayOfWeek, int month)`,
  `EASTER` (an anonymous Gregorian computus, ~10 lines). All of these are `Expr`s that read `context.base().getYear()`,
  reusing the `BASE` check for a missing base date.
- Rolling: an `Anchor` interface (`LocalDate in(int year)`; a weekday/BD anchor steps by day), plus `next(Expr, Anchor)`,
  `prev(Expr, Anchor)`, `bom/eom/boy/eoy(Expr)` and `modFollowing(Expr)`. `nextImm` becomes `next(e, IMM)`, and
  `TOM`/`SPOT` dates are `plus(BASE, 1B)` and `spot(BASE, pair)`.
- `observe(Expr, Map<DayOfWeek, TemporalAmount>)`, `following(Expr)`, `preceding(Expr)`. These reuse `plus(…, market)`
  and `Market.isBusinessDay`.
- `Market withHolidayRules(List<Rule>)`: weekends plus every date that the rules yield for years y-1..y+1 of the asked
  date (an observed date can cross the turn of the year). Evaluation is lazy and needs no cache for the spec sizes.
- The grammar actions stay one-liners, as with `nextImm`/`spot`.

## 3. The ten grammars
Add the date alternatives, the three `>` keywords, `<observance>` in braces and the MONTH/WEEKDAY keywords to:
- antlr `DateSchedule.g4`, coco `Schedule.atg` (still LL(1): after `NUM` the next token chooses the notation),
  javacc `Schedule.jj`, cup `schedule.cup` + `schedule.jflex`, bison `schedule.y` + scanner, sablecc
  `schedule.grammar` + `ScheduleEvaluator`.
- combinators `ScheduleParsers.scala`, peg `schedule.pegjs`, lark `schedule.lark` + Transformer, antlr-python
  `DateSchedule.g4` + visitor.

## 4. Shared spec `examples/schedule.txt`
- Header: explain year-anchored dates, observance, FOLLOWING/PRECEDING, and that `market` lines may hold rules.
- The `market` line is generalised: each item is parsed *by the language itself* and evaluated per year. Today's ISO
  holidays still work, since an ISO date is a rule too. New test market:
  `market XNYS = 1 JAN {SUN + 1D}, 3 MON JAN, 3 MON FEB, EASTER - 2D, LAST MON MAY, 19 JUN {SAT - 1D, SUN + 1D}, 4 JUL {SAT - 1D, SUN + 1D}, 1 MON SEP, 4 THU NOV, 25 DEC {SAT - 1D, SUN + 1D}`
  (Juneteenth without `valid_from`; the cases use years ≥ 2022).
- New section "holiday rules" (~22 cases). Every expected date is cross-checked in Python before the tools run. Examples:
  `[2025-01-01] 3 MON JAN => 2025-01-20`, `[2025-01-01] LAST MON MAY => 2025-05-26`,
  `[2025-01-01] EASTER - 2D => 2025-04-18`, `[2026-01-01] 4 JUL {SAT - 1D, SUN + 1D} => 2026-07-03`,
  `[2022-01-01] 19 JUN {SAT - 1D, SUN + 1D} => 2022-06-20`, `[2022-01-01] 1 JAN {SUN + 1D} => 2022-01-01`,
  `[2025-01-01] 4 THU NOV + 1D => 2025-11-28`, `[2025-01-01 XNYS] 3 FRI APR > PRECEDING => 2025-04-17`,
  `[2025-01-01 XNYS] 1 MAR - 1B => 2025-02-28`, `[2026-07-02 XNYS] T + 1B => 2026-07-06`,
  `[2025-01-01] (1 JAN : 1Q : 31 DEC)`, `4 JUL => error` (no base), `[2025-01-01] 29 FEB => error`,
  `[2025-01-01] 6 MON JAN => error`, and the PAIR/keyword tie `[2025-01-01] T > SPOT EASTER => error`.
- All ten harnesses: `market` items are parsed with `parse()` instead of `LocalDate.parse`. Pattern as in
  `DateSchedulesTest.java`, `ScheduleParsersSuite.scala`, `schedule.test.mjs`, and `tests/test_schedule.py`.

## 5. Planned for later: lunar calendars (TODO.md, no code now)
- Syntax stays open: month keywords per calendar (`1 SHAWWAL`, `10 TISHRI`, `1 LUNAR1`), so the date
  alternative `NUM MONTH` covers them.
- A Hijri date can occur twice in one Gregorian year. The rolling form `T > NEXT 1 SHAWWAL` is always unique; a
  year-anchored `1 SHAWWAL` either yields a list or is an error in such years (to decide when implementing).
- Libraries: Java/Scala `java.time.chrono.HijrahDate` (Umm al-Qura) plus ICU4J for Hebrew/Chinese; Python
  `hijridate`/`convertdate`; JS `Intl.DateTimeFormat` calendars (reading only; finding the Gregorian date needs a search).
  Moon sighting (`method: local_sighting`, scenarios) stays outside the language.
- Also deferred: `conditional` (`24 DEC IF MON..THU`), `duration_span`, named references to other rules,
  per-element adjustment in ranges.

## 6. Docs
- Root README: the language summary, a holiday-rule section with the catalog mapping table, and line and test counts.
- Module READMEs: keyword vs PAIR handling per tool.
- `docs/generate.py`: fix snippet markers that move, update `LINES`, check that `market` lines with rules render.
  Page lesson "Six capitals: keyword or currency pair?"
- `TODO.md`: lunar calendars and the deferred rule types.

## Verification
- Compute the expected dates in Python first (`dateutil.easter` or our computus checked against known Easters 2015–2030).
- In the dev container run `mise run test`: all ten implementations, bison included. Each harness reports the new count.
- Regenerate the page and recount lines. No commit or push unless asked.
