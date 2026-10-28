# date-rule-coco

Calendar dates parsed with [Coco/R](https://ssw.jku.at/Research/Projects/Coco/), an LL(1) compiler generator
that produces a recursive-descent parser and a scanner from an attributed grammar (ATG).

## What it parses

[Dates.atg](src/main/coco/Dates.atg) accepts a comma-separated list of dates in three notations and collects them as `java.time.LocalDate`:

| Notation | Example |
|---|---|
| ISO 8601 | `2015-06-09` |
| German | `09.06.2015` |
| US | `06/09/2015` |

Invalid calendar dates (for example `2015-02-29`) are reported as semantic errors.
Coco/R's error recovery keeps running semantic actions after a syntax error, so the grammar
only collects a date if no error occurred while parsing it.

## Usage

```java
Scanner scanner = new Scanner(new ByteArrayInputStream("2016-02-29, 1.1.2000".getBytes(StandardCharsets.UTF_8)));
Parser parser = new Parser(scanner);
parser.Parse();
parser.errors.count;   // 0
parser.dates;          // [2016-02-29, 2000-01-01]
```

Error messages go to `parser.errors.errorStream` (`System.out` by default).

## Build and test

```
mvn -pl date-rule-coco verify
```

The bundled [Coco.jar](Coco.jar) (version of 2011-04-19) runs through `maven-antrun-plugin` in `generate-sources`.
It uses the frame files [Parser.frame](Parser.frame) and [Scanner.frame](Scanner.frame) from the module root
and writes `Parser`, `Scanner`, and `Errors` to `target/generated-sources/coco` (package `io.github.hjuergens.coco`).
There are 56 TestNG tests: [DatesParserTest](src/test/java/io/github/hjuergens/coco/DatesParserTest.java) and the shared example.
Coco/R always names the generated classes `Parser`, `Scanner`, and `Errors`, so each grammar gets its own package.

## Shared example

Implements the [shared schedule example](../README.md#shared-example-schedule) and is tested against [examples/schedule.txt](../examples/schedule.txt).

- [Schedule.atg](src/main/coco/Schedule.atg) generates into package `io.github.hjuergens.coco.schedule`.
- [Schedules.parse](src/main/java/io/github/hjuergens/coco/schedule/Schedules.java) collects Coco/R's error messages and throws if `errors.count > 0`.
- The semantic actions avoid exceptions (invalid dates become `SemErr`), because error recovery may run them on incomplete input.
- Test: [SchedulesTest](src/test/java/io/github/hjuergens/coco/schedule/SchedulesTest.java).

## Status

Working. Coco/R is licensed under the GPL; it is used only as a build tool, and its output is not covered by the GPL.
