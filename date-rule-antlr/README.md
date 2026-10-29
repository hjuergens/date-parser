# date-rule-antlr

Date expressions and term structures parsed with [ANTLR 4](https://www.antlr.org/) (4.13).
The grammars produce [Joda-Time](https://www.joda.org/joda-time/) periods and `DateTimeAdjuster`s,
which shift a `DateTime` (for example "first day of the next quarter").

This is the most complete module of the project.

## What it parses

| Grammar (`src/main/antlr4/io/github/hjuergens/time/`) | Input | Result |
|---|---|---|
| `Period.g4` | `28D`, `7W`, `14M7W9D`, `1M2D.1Y2M7W` | `org.joda.time.Period` |
| `PeriodTerm.g4` | `+3M1W`, `+2Y3D-7W`, `>Q`, `>=Q`, `^>Q`, `>>>wednesday`, `<<D` | `DateTimeAdjuster` (shift or select) |
| `TermStructure.g4` | `[3M,5M,7M]`, `(3M:1M:9M)`, `[3M,4M,6M]x(4M:1M:9M)` | lists of periods, futures, forward pairs |
| `List.g4` | `(3:5:14)` → 3, 8, 13 | `List<Integer>` |
| `Dates.g4` | `09/06/2015` | parse tree |
| `ISO8601dates.g4` | `2015-06-09` | parse tree (syntax only, no calendar check) |
| `AmbiguousDates.g4` | `32 May 1980 12h` | day and time parts as strings |
| `ICalendar.g4` | iCalendar (RFC 5545) `VEVENT` | parse tree |
| `DateSchedule.g4` | `2015-06-09 + 3M, 09.06.2015`, `(2015-01-15 : 1Q : 2015-12-31)` | `List<java.time.LocalDate>` |
| `Cardinal.g4`, `Unit.g4`, `SimpleCalc.g4`, `DateRule.g4`, `Schedule.g4`, `NewDate.g4` | small building blocks and experiments | |

In `PeriodTerm`, `>` means "next", `<` means "previous", and a trailing `=` means "or this one".
`Q`, `M`, and `D` select the quarter, month, or day; a weekday name selects that day.

## Usage

```java
// shift a date: the first day of the next quarter
DateTimeAdjuster adjuster = SchedulerFactory.parseSchedule(">Q").next();
DateTime next = adjuster.adjustInto(new DateTime(2016, 12, 5, 15, 18));   // 2017-01-01T00:00

// forward periods: pairs of (begin, end) adjusters
TermStructureLexer lexer = new TermStructureLexer(CharStreams.fromString("[3M,4M,6M]x(4M:1M:9M)"));
TermStructureParser parser = new TermStructureParser(new CommonTokenStream(lexer));
List<Pair<DateTimeAdjuster, DateTimeAdjuster>> forwards = parser.forwards().listOut;
```

## Build and test

```
mvn -pl date-rule-antlr verify
```

- The `antlr4-maven-plugin` generates lexers, parsers, listeners, and visitors into `target/generated-sources/antlr4`.
- Unit tests use TestNG and run from the suite in [src/test/resources/testng.xml](src/test/resources/testng.xml).
  **Register new test classes there**, or surefire won't run them.
- [RingCheck](src/test/scala/io/github/hjuergens/util/RingCheck.scala) is a ScalaCheck property test that runs through ScalaTest's `TestNGSuite`.
- [RingSpec](src/integration-test/scala/io/github/hjuergens/util/RingSpec.scala) is a specs2 example run by failsafe in `verify`.
- The `test` phase also runs ANTLR's `TestRig` on [dates.expr](src/test/resources/dates.expr) (exec-maven-plugin).

## Shared example

Implements the [shared schedule example](../README.md#shared-example-schedule) and is tested against [examples/schedule.txt](../examples/schedule.txt).

- [DateSchedule.g4](src/main/antlr4/io/github/hjuergens/time/DateSchedule.g4) computes the values in actions (`returns [LocalDate value]`).
- [DateSchedules.parse](src/main/java/io/github/hjuergens/time/DateSchedules.java) installs a throwing error listener and `BailErrorStrategy`, so the first error ends the parse.
- Test: [DateSchedulesTest](src/test/java/io/github/hjuergens/time/DateSchedulesTest.java).

```java
DateSchedules.parse("(2015-01-15 : 1Q : 2015-12-31)");   // [2015-01-15, 2015-04-15, 2015-07-15, 2015-10-15]
```

## Status and limitations

- Working: all grammars compile with ANTLR 4.13. There are 164 unit tests and 3 integration tests.
- `ISO8601dates` only accepts dates with hyphens: `20150609` is lexed as two `FOUR_DIGIT` tokens and rejected.
- `Unit.g4` defines lexer tokens (`nanos`, `forever`, …) that rule `unit` doesn't accept.
- Uses Joda-Time, which is in maintenance mode; the combinators module uses `java.time` instead.
