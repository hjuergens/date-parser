# date-rule-coco

The [shared schedule example](../README.md#shared-example-schedule) with [Coco/R](https://ssw.jku.at/Research/Projects/Coco/), an LL(1) compiler generator that produces a recursive-descent parser and a scanner from an attributed grammar (ATG),
tested against [examples/schedule.txt](../examples/schedule.txt), [catalog.txt](../examples/catalog.txt) and
[date-holidays.txt](../examples/date-holidays.txt).

The other experiments of this module (the Dates grammar for three date notations) are parked in
[date-rule-experiments/coco](../date-rule-experiments/coco).

## The grammar

- [Schedule.atg](src/main/coco/Schedule.atg) generates into package `io.github.hjuergens.coco.schedule`.
- [Schedules.parse](src/main/java/io/github/hjuergens/coco/schedule/Schedules.java) collects Coco/R's error messages and throws if `errors.count > 0`.
- After `">"`, the grammar branches on `"IMM"` or `"SPOT"` with an optional `pair` token (six capital letters),
  which keeps it LL(1). Coco/R's LL(1) check runs on every build and reports no warnings.
- The semantic actions avoid exceptions (invalid dates become `SemErr`), because error recovery may run them on incomplete input.
  Since they only build functions of base date and market, the old `errors.count == 0` guards around IMM and spot are gone.
- Test: [SchedulesTest](src/test/java/io/github/hjuergens/coco/schedule/SchedulesTest.java).
- Holiday rules and rolling: `3 MON JAN` and `2015-06-09` both start with a number, so `Date` reads it and `AnnualTail` continues;
  the grammar stays LL(1). An attribute whose type contains `>` needs Coco's `<. … .>` brackets, and the `out` attribute must come first.
  `EASTER` matches the token class `pair`, so Coco/R makes it a keyword of that class.
- Lunar months and conditional dates: Lunar month names (`1 SHAWWAL`, `10 TISHRI`, `1 ZHENGYUE`) are more alternatives of `Month`; six-letter ones such as
  `TISHRI` become keywords of `pair` like `EASTER`. `Item` adds `[ "IF" Weekday { Weekday } ]` to a list item, still LL(1).
- Ordinals and sets (`> NEXT 3RD FRI`, `> NEXT {MON, FRI}`): the token `ordinal = digit {digit} ( "ST" | "ND" | "RD" | "TH" )` keeps the count apart from the number that starts `3 FRI JAN`, so the grammar stays LL(1) without factoring; the scanner takes the longer match. Coco/R allows only one `out` attribute, so the count is folded into the anchor (`ScheduleSemantics.times`), and a set is `"{" Target { "," Target } "}"`.
- Selection (`> 3RD WED IN MONTH`, `> 2ND LAST BD IN MONTH`): `Count` has only its one `out` attribute, so counting from the end is a negative count (0 is an error anyway). An annual date starting with `LAST` is only allowed in braces (`Select = Chosen | "{" Target … "}"`), as `LAST` also starts the count; the LL(1) check stays clean.
- Named events: the token `event = '@' (upper | lower) { upper | lower | digit | '_' } { '.' (upper | lower | digit | '_') { upper | lower | digit | '_' } }` starts with a character no other token starts with, so the grammar stays LL(1).

## Rule files

[RuleFile.atg](src/main/coco/RuleFile.atg) reads rule files (`rule KEY for CLASS in SCOPES ... = ...;`, `weekend`,
`calendar`), the same language as [date-rule-antlr-java's RuleFile.g4](../date-rule-antlr-java/README.md#rule-files),
and builds the declarations for [RuleCatalog](src/main/java/io/github/hjuergens/coco/schedule/RuleCatalog.java), the
evaluator shared by the JVM modules. Coco/R names its classes `Parser`, `Scanner` and `Errors` in every grammar, so the
pom generates this one into the subpackage `rules`, where
[RuleFiles](src/main/java/io/github/hjuergens/coco/schedule/rules/RuleFiles.java) is the entry point. Coco/R has a
single start symbol: `RuleFile = { Declaration } [ Exprs ]` reads a rule file or one schedule, which the first token
tells apart, and `RuleFiles` checks which one it got. The keywords need no care: a literal that fits a token class
(`rule` fits `key`, `EASTER` fits `pair`, `MAX` fits `ccy`) becomes a keyword of it. The grammar is LL(1) but for the
step of a series, which keeps the resolver of `Schedule.atg`; Coco/R reports no LL(1) warnings.

```java
RuleCatalog catalog = RuleFiles.parseRules("rule jp.equinox for national_holiday in country:JP = EQUINOX SEP \"Asia/Tokyo\";");
catalog.holidays("country:JP", 2026);   // [Occurrence[date=2026-09-23, rule=jp.equinox, ...]]
```

## Build and test

```
mvn -pl date-rule-coco verify
```

The bundled [Coco.jar](Coco.jar) (version of 2011-04-19) runs through `maven-antrun-plugin` in `generate-sources`.
It uses the frame files [Parser.frame](Parser.frame) and [Scanner.frame](Scanner.frame) from the module root
and writes `Parser`, `Scanner`, and `Errors` to `target/generated-sources/coco/io/github/hjuergens/coco/schedule`.
[SchedulesTest](src/test/java/io/github/hjuergens/coco/schedule/SchedulesTest.java) (TestNG) runs 6105 cases,
[RuleCasesTest](src/test/java/io/github/hjuergens/coco/schedule/RuleCasesTest.java) 59 and [RuleCatalogTest](src/test/java/io/github/hjuergens/coco/schedule/RuleCatalogTest.java) 5.

## Status

Working. Coco/R is licensed under the GPL; it is used only as a build tool, and its output is not covered by the GPL.
