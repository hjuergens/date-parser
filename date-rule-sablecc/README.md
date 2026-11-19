# date-rule-sablecc

The [shared schedule example](../README.md#shared-example-schedule) with [SableCC](https://sablecc.org/) 2, an LALR(1) parser generator that builds a strictly typed syntax tree and tree walkers (visitor pattern),
tested against [examples/schedule.txt](../examples/schedule.txt), [catalog.txt](../examples/catalog.txt) and
[date-holidays.txt](../examples/date-holidays.txt).

The other experiments of this module (the arithmetic grammars `tiny` and `simpleAdder`) are parked in
[date-rule-experiments/sablecc](../date-rule-experiments/sablecc).

## The grammar

[schedule.grammar](src/main/sablecc/schedule.grammar) contains no code at all: SableCC builds the typed syntax tree, and
[ScheduleEvaluator](src/main/java/de/juergens/schedule/ScheduleEvaluator.java) computes the dates from it.
The tree already is the rule: `ScheduleEvaluator.parse` returns a function that walks it with the context
(base date and market), so invalid dates in SableCC only surface when the rule is evaluated.
Elements that occur twice in an alternative need names (`[year]:number`, `[s1]:minus`).
Each kind of shift (`+`, `-`, `> IMM`, `> SPOT [PAIR]`) is an alternative of `shift` and becomes its own node class
(`APlusShift`, …, `ASpotShift`); the optional `pair?` is `null` in the tree when it is missing.
Test: [ScheduleEvaluatorTest](src/test/java/de/juergens/schedule/ScheduleEvaluatorTest.java).

Holiday rules and rolling add the productions `observe`, `anchor` and `annual`; the evaluator gained one method for each.
Lunar month names (`1 SHAWWAL`, `10 TISHRI`, `1 ZHENGYUE`) are more alternatives of `MONTH` (the token
`month` comes before `pair`); `item = expr condition?` with `condition = if weekday+` adds `24 DEC IF MON TUE WED THU`.
The token `easter` is declared before `pair`: on a match of the same length the earlier token wins.
Ordinals and sets (`> NEXT 3RD FRI`, `> NEXT {MON, FRI}`): the token `ordinal = digit+ ('ST' | 'ND' | 'RD' | 'TH')`, `{next} gt next ordinal? anchor` and `anchor = {single} target | {set} l_brace target more_target* r_brace`; `getOrdinal()` is `null` without a count, and the evaluator gets `ASingleAnchor`, `ASetAnchor` and the `A…Target` classes.
Selection (`> 3RD WED IN MONTH`): `{select} gt count select in period_word`. Splitting `annual` into `{fixed} fixed_annual | {last} …` (an annual date starting with `LAST` only in braces, as `LAST` also starts the count) renames the generated classes, e.g. `AMonthDayAnnual` becomes `AMonthDayFixedAnnual`; the evaluator follows.

Named events: the token `event = '@' upper (upper | digit | '_')*` adds the alternatives `{event}` to `date` and `target`, so the evaluator gets the node classes `AEventDate` and `AEventTarget`.

## Rule files

[rulefile.grammar](src/main/sablecc/rulefile.grammar) reads rule files (`rule KEY for CLASS in SCOPES ... = ...;`,
`weekend`, `calendar`), the same language as
[date-rule-antlr-java's RuleFile.g4](../date-rule-antlr-java/README.md#rule-files). As for a schedule, SableCC only builds
the syntax tree: [RuleFiles](src/main/java/de/juergens/schedule/rules/RuleFiles.java) walks it and builds the
declarations for [RuleCatalog](src/main/java/de/juergens/schedule/RuleCatalog.java), the evaluator shared by the JVM
modules. The grammar declares `Package de.juergens.schedule.rules`, so its `node`, `lexer` and `parser` packages don't
mix with those of `schedule.grammar`.
- **One start symbol:** `rule_file = {declarations} declaration* | {schedule} item more_item*` reads a rule file or one
  schedule; the first token tells them apart.
- **Names:** tokens and productions share one name space, so the keyword `ON` is the token `k_on` next to the
  production `on`, and the words of declarations are `l_rule`, `l_for`, ...; they come before `key`, `pair` and `ccy`,
  as the earlier token wins on a match of the same length.
- **LALR(1) without conflicts:** SableCC expands `?` and `*` into productions of its own and stops on a conflict.

```java
RuleCatalog catalog = RuleFiles.parseRules("rule jp.equinox for national_holiday in country:JP = EQUINOX SEP \"Asia/Tokyo\";");
catalog.holidays("country:JP", 2026);   // [Occurrence[date=2026-09-23, rule=jp.equinox, ...]]
```

## Build and test

```
mvn -pl date-rule-sablecc verify
```

`sablecc-maven-plugin` 2.2 (the latest release, from 2008) generates the sources into `target/generated-sources/sablecc`.
[ScheduleEvaluatorTest](src/test/java/de/juergens/schedule/ScheduleEvaluatorTest.java) (TestNG) runs 6097 cases,
[RuleCasesTest](src/test/java/de/juergens/schedule/RuleCasesTest.java) 59 and [RuleCatalogTest](src/test/java/de/juergens/schedule/RuleCatalogTest.java) 5.

## Status

Working. The SableCC Maven plugin is no longer maintained.
