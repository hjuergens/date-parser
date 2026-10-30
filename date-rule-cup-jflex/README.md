# date-rule-cup-jflex

A lexer built with [JFlex](https://jflex.de/) and token definitions built with [CUP](https://www2.cs.tum.edu/projects/cup/),
the classic LALR(1) pair for Java (lex/yacc style).

There are two examples: a lexer for a Pascal subset, and the shared date schedule grammar.

## What it contains

| File | Generates |
|---|---|
| [pascal.cup](src/main/cup/pascal.cup) | `Sym` (token constants) and `PascalParser` (with only a dummy rule) |
| [pascal.jflex](src/main/jflex/pascal.jflex) | `PascalLexer`: keywords, identifiers, integer, real and char literals, operators, `{ comments }` |

The lexer returns `java_cup.runtime.Symbol`s, with the line (`left`) and column (`right`) of each token.

## Usage

```java
PascalLexer lexer = new PascalLexer(new StringReader("x := 42"));   // package io.github.hjuergens
Symbol symbol;
while ((symbol = lexer.next_token()).sym != Sym.EOF) {
    // Sym.IDENT "x", Sym.ASSMNT, Sym.INT 42
}
```

## Build and test

```
mvn -pl date-rule-cup-jflex verify
```

- `com.github.vbmacher:cup-maven-plugin` (CUP 11b) generates into `target/generated-sources/cup`.
- `de.jflex:jflex-maven-plugin` (JFlex 1.9) generates into `target/generated-sources/jflex`.
- There are 195 TestNG tests: [PascalLexerTest](src/test/java/io/github/hjuergens/PascalLexerTest.java) and the shared example.

For `pascal.cup`, CUP warns that the terminals are "declared but never used". That's expected while that grammar has no rules.

## Shared example

Implements the [shared schedule example](../README.md#shared-example-schedule) and is tested against [examples/schedule.txt](../examples/schedule.txt).

- [schedule.cup](src/main/cup/schedule.cup) uses left recursion for lists and period chains, which is natural for LALR(1).
- CUP has no optional elements, so `> SPOT [PAIR]` is written as two alternatives.
- [schedule.jflex](src/main/jflex/schedule.jflex) is its lexer: longest match separates `IMM`, `SPOT`, currency pairs (`[A-Z]{6}`) and unit letters. `%cupsym ScheduleSym` must come before `%cup`, otherwise the EOF action refers to `sym`.
- CUP runs once per grammar (two plugin executions); JFlex processes every `.jflex` file.
- `Schedules.parse(text)` throws on the first syntax error ("Can't recover from previous error(s)").
- Test: [SchedulesTest](src/test/java/io/github/hjuergens/schedule/SchedulesTest.java).
- Holiday rules and rolling: the `'>'` token is now `GT`, since `NEXT` is a keyword. In the JFlex spec, `"EASTER"` comes before
  `[A-Z]{6}`: on a match of the same length the earlier rule wins.

## Status

Working. The Pascal CUP grammar is a skeleton: it has no productions apart from a dummy rule.
