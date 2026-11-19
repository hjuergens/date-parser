# date-rule-combinators

The [shared schedule example](../README.md#shared-example-schedule) with
[Scala parser combinators](https://github.com/scala/scala-parser-combinators) (Scala 2.13), tested against
[examples/schedule.txt](../examples/schedule.txt), [catalog.txt](../examples/catalog.txt) and
[date-holidays.txt](../examples/date-holidays.txt).

The natural-language parsers that used to live here ("next sunday", "2 London business days prior to the 3rd Wednesday
of the delivery month") are parked in [date-rule-experiments/combinators](../date-rule-experiments/combinators).

## The parser

[ScheduleParsers](src/main/scala/de/juergens/text/ScheduleParsers.scala) is plain Scala:
the grammar is a set of `Parser` values, and `foldLeft` applies the shifts (periods, `> IMM` and `> SPOT`) from left to right.
The parsers return functions of a `Context` (base date `T` and `Market`), applied when the rule is evaluated.
Business days (`B`) are a small case class `BusinessDays` that implements `java.time.temporal.TemporalAmount`.
Without a separate lexer, `>SPOTUSDCAD` without a space parses as well (as in the PEG module); the tools with a lexer reject it.
Equinoxes and solstices come from [Season](src/main/scala/de/juergens/time/Season.scala).

```scala
ScheduleParsers.parse("T + 1W - 2D")(Context(Some(LocalDate.of(2015, 6, 9))))   // List(2015-06-14)
```

Holiday rules and rolling are more parsers (`annual`, `anchor`, `observe`). Without a lexer, order matters (`"TOM"` before `"T"`),
and the pair pattern `(?!EASTER|TISHRI|…)[A-Z]{6}` excludes the six-letter keywords, or `T > SPOT EASTER` would parse.
Lunar month names extend the `month` regex, where `ADARII` must come before `ADAR`: a regex alternation takes the first match.
Ordinals and sets (`> NEXT 3RD FRI`, `> NEXT {MON, FRI}`): `counted = opt(ordinal) ~ anchor`, where `ordinal` is the regex `[0-9]+(ST|ND|RD|TH)` and fails (`>>` with `failure`) when the suffix doesn't fit the number; a set is `"{" ~> rep1sep(target, ",") <~ "}"`.

Named events: `eventName` is the regex `@[A-Z][A-Z0-9_]*`, tried first in `date` and last in `anchor`; a regex token can't contain the whitespace that `RegexParsers` skips, so `@ FOMC` is an error as with the lexers.

## Rule files

[RuleFiles](src/main/scala/de/juergens/text/rules/RuleFiles.scala) reads rule files (`rule KEY for CLASS in SCOPES ...
= ...;`, `weekend`, `calendar`), the same language as
[date-rule-antlr-java's RuleFile.g4](../date-rule-antlr-java/README.md#rule-files). Its parsers build Java objects:
the declarations of [RuleCatalog](src/main/java/de/juergens/text/RuleCatalog.java) with
[ScheduleSemantics](src/main/java/de/juergens/text/ScheduleSemantics.java), the evaluator and semantics all JVM modules
share (in `src/main/java`, which javac compiles before scalac), so the schedule parsers above keep their own Scala
semantics while rule files are evaluated as in the other JVM modules.
- **Words:** without a lexer, `kw` matches a word only with a word boundary after it, so `T` is not the start of `TOM`
  and `OR` not the start of `ORTHODOX`; regexes of months, pairs and currencies end with `(?![A-Z])`.
- **Order:** `|` doesn't try an alternative again once an earlier one succeeded (as in a PEG), so a scope comes before
  a key, which would take its first word, and the annual dates before the dates that also start with a number.
- **References:** a parser can backtrack after an `@reference`, so the references and the Hijri methods of a rule are
  read from the text of its declaration (`withText`), without strings and comments.
- **Comments:** `whiteSpace` also skips `#` to the end of the line.

```scala
val catalog = RuleFiles.parseRules("rule jp.equinox for national_holiday in country:JP = EQUINOX SEP \"Asia/Tokyo\";")
catalog.holidays("country:JP", 2026)   // [Occurrence[date=2026-09-23, rule=jp.equinox, ...]]
```

## Build and test

```
mvn -pl date-rule-combinators verify
```

[ScheduleParsersSuite](src/test/scala/de/juergens/text/ScheduleParsersSuite.scala) (ScalaTest) runs 6097 cases,
[RuleCasesSuite](src/test/scala/de/juergens/text/rules/RuleCasesSuite.scala) the 59 of [examples/rules.txt](../examples/rules.txt)
and [RuleCatalogSuite](src/test/scala/de/juergens/text/rules/RuleCatalogSuite.scala) 5 tests of attributes and announcements;
[SeasonTest](src/test/scala/de/juergens/time/SeasonTest.scala) (JUnit 4) checks the equinoxes and solstices.
