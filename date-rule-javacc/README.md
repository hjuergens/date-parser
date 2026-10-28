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
`Schedule.parse(text)` throws `ParseException` (syntax), `TokenMgrException` (unknown character), or `DateTimeException`.
Test: [ScheduleTest](src/test/java/io/github/hjuergens/parser/ScheduleTest.java).

## Status

Working.
