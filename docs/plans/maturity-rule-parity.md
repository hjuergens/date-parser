# Plan: the schedule language at least as expressive as MaturityRule

Status: approved 2026-11-04; steps 1 (brackets, prefix), 2 (selection, weeks) and 3 (series) done, step 4 next. Builds on [intervals-and-series.md](intervals-and-series.md).

## Context
The goal: the schedule language ("date-rule") to express every MaturityRule rule. The source is
the MaturityRule language description and its usage notes (IMM, BOM/EOM, DOL series).

- Already covered:
  - shifts `from` (`+ nD/nB/nW/nM/nQ/nH/nY`);
  - `monday(n) from` (`> NEXT nTH MON`, `> PREV …`);
  - IMM;
  - `or` of weekdays and dates (sets);
  - FX spot.
- Decided:
  - `on`, `onn`, `tn`, `tnn`, `forcedOn` stay out, because the current language can already express them;
  - `fix` and `fixing(n)` come in, with a second, **fixing calendar**;
  - plan first, then in steps.

`docs/plans/intervals-and-series.md` (committed) already designs most of what is missing: selection, series, merging,
`IF {…}`, the test prefix. This plan builds on it with three changes:
1. Picks become required, and they count from T as MaturityRule's `next` does.
2. Weeks become a period (`BOW`/`EOW`, `IN WEEK`).
3. A fixing market is added.

This document is that plan; the intervals plan points here.

## The gap, selector by selector (the parity table, also in the plan doc and in the spec section)
| MaturityRule | Schedule language | Status |
|---|---|---|
| `day(n)`/`business(n)`/`week(n)`/`month(n)`/`quarter(n)`/`halfyear(n)`/`year(n)` **from** | `+ nD`, `+ nB`, `+ nW`, `+ nM`, `+ nQ`, `+ nH`, `+ nY` | done |
| `monday(n) from` | `> NEXT nTH MON` (n < 0: `> PREV`) | done |
| `yyyy-mm-dd`, `today`, `sp` | `2026-11-02`, `T`, `SPOT [PAIR]` | done |
| `firstday`/`lastday of` P | `> BOM`/`> EOM` … `> BOY`/`> EOY`; new: `> BOW`/`> EOW` | BOW/EOW new |
| `day(n) of` P | `> BOM + (n-1)D` (likewise for each period) | done |
| `wednesday(n) of` P, `business(n) of` P | `> nTH WED IN MONTH`, `> nTH BD IN MONTH`, `> LAST BD IN MONTH`, `> 2ND LAST …` | step 2 |
| `fixing(n) of` P | `> nTH FD IN MONTH` | step 6 |
| `month(n) of` Date (and week, quarter, …) | `+ (n-1)M > BOM`: the period of the date shifted by n-1 | done (translation) |
| `month(n) of` P (nth month of a quarter, …) | `> BOQ + (n-1)M`; weeks: `> BOM > BOW + (n-1)W` (the week holding the first day) | translation, BOW new |
| `january(n) of` Date | `> BOM - 1D > NEXT nTH 1 JAN` (the current January counts) | translation |
| `every(k, P)` | series `[X : kP : ]`, shifts after it apply to each element | steps 3 and 5 |
| `next(n, seq)` (nth result after **today**) | pick `nTH [X : kP : ] …`: nth date after T, counting the dates after the shifts | step 5 |
| `a or b` | sets `{…}`, merge `+`/`-` of series | step 4 |
| `fix` | `> FIX [PAIR]`: the fixing date of a value date | step 6 |
| `fixing(n) from` | `+ nF` | step 6 |
| `<k>` maturity series (DOL) | series: `[SPOT : 1M : SPOT + 19M] > EOM + 2B` | step 3 |
| `on`, `onn`, `tn`, `tnn`, `forcedOn` | left out (decided); e.g. `TOM`, `T + 1B`, `T + 1D` | — |

## Steps (each: Lark first, then the shared semantics, then the twelve grammars, full run on both machines, commit)
1. **Prefix and ranges** (intervals plan §6 and §2, part one):
   - every harness uses the narrowed prefix regex, extended for the fixing market:
     `\[(\d{4}-\d{2}-\d{2})(?: ([A-Z0-9_]+))?(?: ([A-Z0-9_]+))?\] ` (base, market, fixing market);
   - the range `( : : )` moves to `[ : : ]` in the spec, `scripts/catalog_cases.py`, `scripts/date-holidays/generate.mjs`,
     docs and playground;
   - the counts don't change.
2. **Selection** (intervals plan §1) plus **weeks**:
   - `> count select IN PERIOD` with `PERIOD = WEEK | MONTH | QUARTER | HALFYEAR | YEAR`;
   - new rolls `BOW`/`EOW`: ISO week, Monday to Sunday;
   - the weekday targets of `NEXT` stay as they are.
3. **Series as items** (intervals plan §2, §4):
   - `[a : step : b]`, `]a : b[`, half-open forms;
   - shifts apply to each element;
   - series in lists and with `IF`; `IF {…}`.
4. **Merging** (intervals plan §3): `+`/`-` of series and sets, sorted, without duplicates.
5. **Picks and open-ended series**, now required:
   - `pick = count collection {shift}`, where count is `ORDINAL | LAST | ORDINAL LAST`;
   - an open end `[X : kP : ]` is only valid under a pick;
   - semantics: the dates of the series after the shifts, in order, are counted from the first one **strictly after T**
     (MaturityRule: "from today's perspective", which also gives a future first occurrence when T is itself one);
   - `LAST` with an open end is an error;
   - the evaluation stops after the nth hit, with a guard of 10 000 elements; nothing found is an error.
   - Example: `5TH [SPOT : 1Q : ] > EOQ > 3RD WED IN MONTH`, which must equal `SPOT > NEXT 5TH IMM`.
6. **Fixing calendar**:
   - the context gets a `fixing` market beside the settlement market. Without one, fixing days are Monday to Friday,
     as business days are without a market;
   - `F` as a unit (`+ 2F` = two fixing days), `FD` as a target (`> NEXT FD`, `> 3RD FD IN MONTH`);
   - `> FIX [PAIR]` = value date minus the pair's spot lag (2, or 1 for USD against CAD/TRY/PHP/RUB) in fixing days.
     It is the inverse of `> SPOT` with the fixing calendar. Errors: none expected beyond those of `> SPOT`;
   - `F` and `FD` are new keywords. Neither has six letters, so no clash with pairs; `F` was free among the units.
   - Spec: `market` lines stay; the prefix's third field names a market used for fixing.
7. **Parity section in the spec**:
   - "MaturityRule" in `examples/schedule.txt` gets a case per row of the table above, with the MaturityRule expression
     as a comment;
   - plus every rule of the MaturityRule usage notes: IMMH/M/U/Z and their `1`/`2` variants, the BOM_/EOM_ quarter
     boundaries, the DOL series, and the "Common patterns";
   - every expected date is checked in Python first;
   - the existing tricks (`SPOT + 1M > BOM - 1D > NEXT 3RD WED`) stay as cases beside the direct form.

## Files (pattern repeated per tool; representative paths)
- Spec: `examples/schedule.txt`. Generators: `scripts/catalog_cases.py`, `scripts/date-holidays/generate.mjs` (range syntax only).
- Grammars:
  - `date-rule-lark/src/lark_schedule/schedule.lark`, `date-rule-antlr-java/src/main/antlr4/.../DateSchedule.g4`,
    `date-rule-antlr-python/DateSchedule.g4`;
  - `date-rule-coco/src/main/coco/Schedule.atg`, `date-rule-javacc/src/main/javacc/Schedule.jj`, `date-rule-cup-jflex/src/main/{cup,jflex}`,
    `date-rule-bison/src/main/resources/schedule.y`, `date-rule-sablecc/src/main/sablecc/schedule.grammar` (+ evaluator);
  - `date-rule-combinators/.../ScheduleParsers.scala`, `date-rule-peg/schedule.pegjs`, `date-rule-instaparse/.../schedule.ebnf`,
    `date-rule-flex-bison/src/{scanner.l,parser.y,ast.h}`.
- Semantics:
  - `ScheduleSemantics.java` (date-rule-antlr-java, copied to the JVM modules), `semantics.py` (Lark, copied to antlr-python),
    the Peggy initializer, `date-rule-flex-bison/src/semantics.c`;
  - `Context` gains `fixing`.
- Harnesses:
  - the 12 test readers; `date-rule-flex-bison/test/spec.c` and `go/internal/spec/spec.go`;
  - the Go `Context` gains `Fixing`, and `schedule.h` gains `Market *fixing`.
- Docs: README (EBNF, parity paragraph), module READMEs (a note per tool if it needed a trick), `docs/page_template.html` (EBNF, chips,
  playground examples), `docs/generate.py`, regenerate `docs/index.html`.

## Verification
- Before each step: the expected dates of the new cases are computed in Python (Lark semantics), so the spec is right
  before any tool runs.
- Per step: Lark locally is OK (light). Everything else, including every Docker build, runs on the two test machines
  through the `ci-run` snapshot. Full `mise run test` on both machines;
  the counts go up by the new cases in every module (Lark ×2). The C module also runs under ASan/UBSan, and Go with `-race`.
- At the end, the parity check: every row of the table has at least one case, and every example of the MaturityRule
  document and of its usage notes is a passing case.
- Before each commit: the host-name grep, delete `ci-run`, commit and push only when the maintainer says so.

## Decisions taken in this plan (changeable)
- `next` counts **strictly after T**.
- Without a fixing market, fixing days are Monday to Friday.
- `FIX` uses the spot lag of the pair.
- `january(n) of Date` counts the current January.
- The nth week of a period is the one holding its first day.
- Picks stop after 10 000 elements.
