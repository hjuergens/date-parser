# Plan: named events in the schedule language, and date-holidays as a second case generator

Status: Part A (named events) is implemented in all ten modules, with 19 cases in `examples/schedule.txt` and the
FOMC-anchored catalog rules (HKMA, SAMA, CBO, CBUAE base rates, FOMC minutes) in `examples/catalog.txt`. Two details
changed while implementing: an event may be the input of `> NEXT`/`> PREV` and is then evaluated per date (only a range
end needs a single date), and the generator groups a rule's dates by the year of the anchor, as `@FOMC + 21D` shifts
December meetings into January. Part B (date-holidays) is next.

## Context
The schedule language expresses every *computed* rule of the holidays-events catalog. The gaps are dates an
authority announces (FOMC, AGMs) and rules anchored to them: 9 in the catalog (`hkma.base_rate`,
`sama.rate_decision`, `fed.fomc_minutes`, issuers' ex-dividend dates, …) and 6 in the finance-dates research.
A market is already declared from data (`market X = …`, items parsed by the language). The same mechanism,
**named events**, brings announced dates in as data and lets rules refer to them, which also gives the TODO item
"references to other named rules" for free. Part B is the "short check" from before: date-holidays (npm), whose
own rule language (`easter -2`, `3rd monday in June`, `12-25 and if sunday then next monday`) is the closest relative
of this grammar. It would be a second, much broader independent oracle beside holidays-events.

Findings of the check (date-holidays 3.37.0, published 2026-09-20; 3.38.0 is only a week old):
code ISC, **data CC BY 3.0** (attribution required in the generated file); 207 countries, 1711 distinct rule
strings (4805 uses). Rough classification of the distinct rules: ~750 expressible now (fixed, nth/last weekday,
`weekday after MM-DD`, Easter, Hijri, Hebrew, Chinese, `if sunday then next monday`, `on/not on <weekdays>`),
~400 one-off dates, ~380 substitutes / "observe both" (expressible with `+ kD IF <weekday>`, see B2),
~107 with `since`/year ranges, 32 Orthodox, 38 with time of day, a few Persian, Bengali, equinox, solar terms,
bridge days. Holidays returned by `getHolidays(year)` carry their `rule` string, so expected dates per rule are direct.

## Part A: named events

### A1. Language
```
date   = … | EVENT .                 (* every occurrence in the year of T *)
anchor = … | EVENT .                 (* T > NEXT @FOMC: the first occurrence strictly after *)
EVENT  = "@" UPPER { UPPER | DIGIT | "_" } .
```
- Declared like markets, as a line of the spec file: `event FOMC = 2025-01-29, 2025-03-19, …` or
  `event THANKSGIVING = 4 THU NOV`. Items are schedules parsed by the language itself, so an event is a named rule.
- `@FOMC` is multi-valued: the item yields one date per occurrence in T's year, the shift chain applied to each
  (`@FOMC + 21D`, `@FOMC + 1B`, `@FOMC IF WED`). No occurrence → no date (`none`).
- In a range endpoint and as `> NEXT/PREV` anchor input an expr must be single-valued; several occurrences → error
  (the same rule as a lunar date that occurs twice).
- Errors: undeclared name, missing base date.
- The sigil `@` keeps the name out of every lexer's keyword/PAIR conflicts (`@EASTER` is a name, not a keyword).
- Context gains `events: name → rule`, beside `market`.

### A2. Implementations (pattern per language, as for holiday rules)
- Semantics: `ScheduleSemantics.java` (date-rule-antlr, copied to the 5 other JVM modules), the companion of
  `ScheduleParsers.scala`, the initializer of `schedule.pegjs`, `semantics.py` (Lark, copied to antlr-python):
  `event(name)` → occurrences in the base year; a multi-valued expr = list of starts mapped through the shifts
  (`expr` already applies shifts in a loop, e.g. `Rules.expr` in `date-rule-lark/src/lark_schedule/__init__.py`);
  the `NEXT/PREV` anchor for an event = min/max occurrence after/before, searching the year and the next.
- Grammars: token `EVENT` + the two alternatives in `DateSchedule.g4` (both), `Schedule.atg`, `Schedule.jj`,
  `schedule.cup`/`schedule.jflex`, `schedule.y` + scanner, `schedule.grammar`, `ScheduleParsers.scala`,
  `schedule.pegjs`, `schedule.lark`.
- Harnesses (`DateSchedulesTest`-style in each module, `ScheduleParsersSuite.scala`, `schedule.test.mjs`,
  `tests/test_schedule.py`): read `event NAME = …` lines like `market` lines, into the context.

### A3. Spec `examples/schedule.txt`, section "named events" (~12 cases)
`event FOMC = <2025 decision dates>` (taken from the holidays-events FOMC observations), `event THANKSGIVING = 4 THU NOV`;
cases: `@FOMC` (8 dates), `@FOMC + 21D`, `[… XNYS] @FOMC + 1B`, `@FOMC IF WED`, `[2025-02-01] T > NEXT @FOMC`,
`[2025-12-15] T > NEXT @FOMC => error`, `[2024-01-01] @FOMC => none`, `@THANKSGIVING + 1D`, `@NOPE => error`,
`@FOMC => error` (no base), `(@FOMC : 1D : 31 DEC) => error`, `@EASTER => error` (undeclared, not the keyword).

### A4. Catalog generator `scripts/catalog_cases.py`
- `relative_to_event` anchored to an `announcement_only` rule: declare `event <RULE_KEY>` with the anchor's observed
  dates from holidays-events' `data/bucket/observations/<source>/…/*.jsonl` (dedupe by `event_key`, take
  `local_start_date`, which is what `RuleEngine._relative` anchors on), translate as `@<KEY> + nB` / `+ nD`, and take
  the expected dates from `engine._relative(...)` fed with those occurrences. No observations → stays skipped
  ("no observed dates").
- Anchor with several versions: translate the anchor's version valid in the case's year (fixes the second gap).
- Out of this plan: the UAE "Ramadan 30 if the month has 30 days" (stays in TODO).

### A5. Docs
README (EBNF, language paragraph, catalog numbers), module READMEs (event token per tool), `docs/page_template.html`
(EBNF, example, lesson if a tool needed a trick), `docs/generate.py` LINES, regenerate `docs/index.html`; TODO.md:
named references done. Save this plan as `docs/plans/named-events.md` like `docs/plans/holiday-rules.md`.

## Part B: date-holidays cases

### B1. Generator `scripts/date-holidays/` (Node, because the expected dates come from date-holidays itself)
`package.json` pinning `date-holidays` 3.37.0 (an external package, installed only with approval), `generate.mjs`:
for each country (and its states/regions), each year 2024–2027, `getHolidays(year)` grouped by `rule`; translate the
rule string; keep the case only in years where the library yields the rule (that respects `active`/`disable`
without modelling them); dedupe by (schedule, year). Lunar holidays start at 18:00 the evening before in
date-holidays: take the day itself (the end date), and if its Hijri/Hebrew calendar method still differs from ours
(Umm al-Qura, pyluach/ICU), skip those rules with that reason after the first comparison. Output
`examples/date-holidays.txt` with an attribution header (CC BY 3.0, source URL, version) and, at the end, the untranslated
rules counted per reason (not one line each). Expected size: ~3–4k cases.

### B2. Translation (regex translator, shapes in order of frequency)
`MM-DD` → `25 DEC`; `YYYY-MM-DD` → ISO date; `easter ±n` → `EASTER ± nD`; `<n>th <wd> in <Month>` → `n WD MON`;
`<wd> after MM-DD` → `D MON - 1D > NEXT WD`; `<wd> before MM-DD` → `D MON > PREV WD`; Hijri/Hebrew/Chinese names
→ our MONTH keywords; `if sunday then next monday …` → observance `{SUN + 1D, SAT - 1D}`;
`on <wds>` / `not on <wds>` → `IF …`; `substitutes D if w then next t` → `D + kD IF t` (k = days w→t; the weekday of
`D + k` is t exactly when D is w); `D and if w then next t` → `D, D + kD IF t`. Everything else (Orthodox, time,
durations, `since`, Persian, Bengali, equinox, solar terms, `if is holiday`) → skip with reason; the counts tell
which language feature to add next.

### B3. Harnesses
All ten read a list of spec files; add `date-holidays.txt` to each list (Java array in each `*Test`, Scala `Seq`,
Peggy `files`, Python `cases(file)` parametrisation). README/page: a paragraph and the attribution.

## Order, commits, runs
A (language + spec + catalog) → Lark locally → full run on another machine → fix → commit/push "Named events". Then B → same cycle → commit/push "Test against date-holidays".
Counts updated in each commit.

## Verification
- Every expected date of the new spec cases cross-checked in Python before the tools run.
- Full run: `mise run test` passes in all ten modules; counts = 2 × cases for Lark, cases (+fixed extras) elsewhere.
- `catalog.txt`: the anchored rules move from the skip list into cases (e.g. `fed.fomc_minutes`, `hkma.base_rate`).
- `date-holidays.txt`: header attribution present; skip summary lists the reasons with counts.
