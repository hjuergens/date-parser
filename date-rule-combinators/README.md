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

## Build and test

```
mvn -pl date-rule-combinators verify
```

[ScheduleParsersSuite](src/test/scala/de/juergens/text/ScheduleParsersSuite.scala) (ScalaTest) runs 6070 cases;
[SeasonTest](src/test/scala/de/juergens/time/SeasonTest.scala) (JUnit 4) checks the equinoxes and solstices.
