# date-rule-javacc

A brace-matching grammar built with JavaCC-style tooling: `ph-javacc-maven-plugin`, which uses
[ParserGeneratorCC](https://github.com/phax/ParserGeneratorCC), a maintained fork of [JavaCC](https://javacc.github.io/javacc/).
It generates an LL(k) recursive-descent parser.

The module also collects JUnit 4 experiments (Theories, parameterized and dynamic suites).

## What it parses

[Simple1.jj](src/main/javacc/Simple1.jj) is the classic JavaCC example: exactly one group of matched braces, optionally followed by line breaks.

| Input | Result |
|---|---|
| `{}`, `{{{}}}`, `{}\n` | accepted |
| `{{}`, `{}}`, `{}{}` | `ParseException` |
| `{c}` | `TokenMgrException` (unknown character) |

## Usage

```java
Simple1 parser = new Simple1(new StringReader("{{}}"));
parser.Input();
```

ParserGeneratorCC generates `Reader` constructors only. The JavaCC `InputStream` constructors don't exist,
so wrap streams in an `InputStreamReader`.

## Build and test

```
mvn -pl date-rule-javacc verify
```

Sources are generated into `target/generated-sources/javacc1`. Tests are JUnit 4:
[Simple1AcceptanceTest](src/test/java/io/github/hjuergens/parser/Simple1AcceptanceTest.java) and `Simple1Test` cover the parser.
The classes in `de.juergens.util` are JUnit runner experiments; several are `@Ignore`d (7 skipped).

## Shared example

Implements the [shared schedule example](../README.md#shared-example-schedule) and is tested against [examples/schedule.txt](../examples/schedule.txt).

[Schedule.jj](src/main/javacc/Schedule.jj) is LL(1): each production declares its locals and returns its value.
After `">"` the production branches on `"IMM"` or `"SPOT"` followed by an optional `<PAIR>`; `Period()` returns a
`TemporalAmount`, because business days (`B`) aren't a `java.time.Period`.
`Schedule.parse(text)` returns a `ScheduleSemantics.Rule` (evaluate it with base date and market), or throws `ParseException` (syntax),
`TokenMgrException` (unknown character), or `DateTimeException`.
Test: [ScheduleTest](src/test/java/io/github/hjuergens/parser/ScheduleTest.java).

Holiday rules and rolling: `Date()` reads the number that starts both `2015-06-09` and `3 MON JAN`, then `AnnualTail(n)` continues.
The tokens `MONTH`, `WEEKDAY`, `ROLL` and `EASTER` are declared before `PAIR`: on a match of the same length the earlier one wins.
Lunar month names (`1 SHAWWAL`, `10 TISHRI`, `1 ZHENGYUE`) are more alternatives of `MONTH`; `Item()` adds
`( "IF" ( <WEEKDAY> )+ )?` to a list item. `ScheduleTest` runs 2414 cases (with the case number in each name: two cases
differ only in their base date).
Named events: the token `< EVENT : "@" ["A"-"Z"] (["A"-"Z", "0"-"9", "_"])* >` is one more choice in `Date()` and `Anchor()`; no lookahead is needed, as no other token starts with `@`.

## Status

Working.
