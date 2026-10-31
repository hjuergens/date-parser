# date-rule-javacc

A brace-matching grammar built with JavaCC-style tooling: `ph-javacc-maven-plugin`, which uses
[ParserGeneratorCC](https://github.com/phax/ParserGeneratorCC), a maintained fork of [JavaCC](https://javacc.github.io/javacc/).
It generates an LL(k) recursive-descent parser.

There is no date grammar yet. The module also collects JUnit 4 experiments (Theories, parameterized and dynamic suites).

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

## Status

Working. It's a demonstration grammar, not a date grammar.
