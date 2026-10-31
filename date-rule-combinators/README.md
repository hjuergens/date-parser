# date-rule-combinators

Natural-language date rules such as "next sunday", "three days after next sunday", or
"2 London business days prior to the 3rd Wednesday of the delivery month", parsed with
[Scala parser combinators](https://github.com/scala/scala-parser-combinators) (Scala 2.13).
Results are `java.time` `TemporalAdjuster`s (`LocalDateAdjuster`).

## What it parses

Parsers live in [src/main/scala/de/juergens/text](src/main/scala/de/juergens/text) and can be combined by mixing in traits:

| Parser | Examples |
|---|---|
| `DateRuleParsers.adjuster` | `next thursday`, `tomorrow`, `three weeks ago`, `three days after next sunday`, `first Wednesday in June` |
| `TemporalAdjustersParsers` | `first day of next month`, `last day of year`, `last monday in month`, `previous or same friday` |
| `DateParsers` | weekdays, months, month-day |
| `NumberParsers` / `TermParsers` | cardinals (`three`), ordinals (`3rd`, `third`), periods (`3 days`) |
| `SeasonParsers` | `spring`, `summer`, `autumn`/`fall`, `winter` |
| finance parsers in `SpecialParsers` | IMM dates, last trading day rules |

Example phrases are in [src/test/resources](src/test/resources), one per line.
Domain types (`Ordinal`, `Cardinal`, `Direction`, `Sign`, `BusinessDay`, `QuarterAdjuster`, …)
are in `de.juergens.time` and `de.juergens.util`.

## Usage

```scala
import java.time.LocalDate
import de.juergens.text.DateRuleParsers

val parser = new DateRuleParsers
val result = parser.parseAll(parser.adjuster, "next thursday")
if (result.successful)
  println(LocalDate.of(2016, 6, 15).`with`(result.get))   // 2016-06-16
```

## Build and test

```
mvn -pl date-rule-combinators verify
```

The tests mix four frameworks:

- JUnit 4 and TestNG classes run on the JUnit Platform (vintage and TestNG engines) through surefire.
  It includes `*Test`, `*TestNG`, and `*$Test` classes.
- ScalaTest suites (`*_FunSpec`, `JulianDate$Test`, …) run with `scalatest-maven-plugin` in the `test` phase.
- specs2 specifications that use `JUnitRunner` run through the vintage engine.

`verify` also runs scalastyle, CPD duplicate-code detection on Scala sources, and the scoverage check.
The test-only dependency `org.jquantlib:jquantlib:0.2.4` is installed from the repository's [repo/](../repo) directory by the parent build.

## Shared example

Implements the [shared schedule example](../README.md#shared-example-schedule) and is tested against [examples/schedule.txt](../examples/schedule.txt).

[ScheduleParsers](src/main/scala/de/juergens/text/ScheduleParsers.scala) is plain Scala:
the grammar is a set of `Parser` values, and `foldLeft` applies the shifts (periods, `> IMM` and `> SPOT`) from left to right.
The parsers return functions of a `Context` (base date `T` and `Market`), applied when the rule is evaluated.
Business days (`B`) are a small case class `BusinessDays` that implements `java.time.temporal.TemporalAmount`.
Without a separate lexer, `>SPOTUSDCAD` without a space parses as well (as in the PEG module); the tools with a lexer reject it.
Test: [ScheduleParsersSuite](src/test/scala/de/juergens/text/ScheduleParsersSuite.scala).

```scala
ScheduleParsers.parse("T + 1W - 2D")(Context(Some(LocalDate.of(2015, 6, 9))))   // List(2015-06-14)
```

Holiday rules and rolling are more parsers (`annual`, `anchor`, `observe`). Without a lexer, order matters (`"TOM"` before `"T"`),
and the pair pattern `(?!EASTER|TISHRI|…)[A-Z]{6}` excludes the six-letter keywords, or `T > SPOT EASTER` would parse.
Lunar month names extend the `month` regex, where `ADARII` must come before `ADAR`: a regex alternation takes the first match.

## Status

- Working: 155 JUnit/TestNG tests and 2358 ScalaTest tests. One TestNG test is disabled (`firstDayOfNextMonthTest`).
- `QuarterAdjuster(n)` moves to another quarter **within the same year** (Q4 + 1 → Q1 of the same year).
- Several rules in [high_sophisticated.txt](src/test/resources/complex/high_sophisticated.txt) are still commented out,
  for example "every friday the 13th of month" and "full moon next after the vernal equinox".
