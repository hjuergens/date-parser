# date-rule-sablecc

Arithmetic expressions parsed with [SableCC](https://sablecc.org/) 2, an LALR(1) parser generator
that builds a strictly typed syntax tree and tree walkers (visitor pattern).

The arithmetic grammars show how SableCC works; the shared example below is a date grammar.

## What it parses

| Grammar ([src/main/sablecc](src/main/sablecc)) | Package | Input |
|---|---|---|
| `tiny.grammar` | `de.juergens.tiny` | `4+(6*8)/3`: `+ - * / %` with precedence, parentheses |
| `simpleAdder.grammar` | `de.juergens.simpleAdder` | same language, generated separately |

SableCC generates `lexer`, `parser`, `node` (one class per alternative, such as `APlusExpr`), and `analysis`
(`DepthFirstAdapter`, `AnalysisAdapter`) packages.

## Usage

```java
Parser parser = new Parser(new Lexer(new PushbackReader(new StringReader("4+(6*8)/3"), 1024)));
Start ast = parser.parse();            // ParserException / LexerException on invalid input
ast.apply(new DepthFirstAdapter() {    // walk the tree
    @Override public void outAPlusExpr(APlusExpr node) { /* ... */ }
});
```

[EvaluatorTest](src/test/java/de/juergens/tiny/parser/EvaluatorTest.java) contains a complete evaluator that uses a stack.

## Build and test

```
mvn -pl date-rule-sablecc verify
```

`sablecc-maven-plugin` 2.2 (the latest release, from 2008) generates the sources into `target/generated-sources/sablecc`.
There are 47 TestNG tests.

## Shared example

Implements the [shared schedule example](../README.md#shared-example-schedule) and is tested against [examples/schedule.txt](../examples/schedule.txt).

[schedule.grammar](src/main/sablecc/schedule.grammar) contains no code at all: SableCC builds the typed syntax tree, and
[ScheduleEvaluator](src/main/java/de/juergens/schedule/ScheduleEvaluator.java) computes the dates from it.
Elements that occur twice in an alternative need names (`[year]:number`, `[s1]:minus`).
Test: [ScheduleEvaluatorTest](src/test/java/de/juergens/schedule/ScheduleEvaluatorTest.java).

## Status

Working. The SableCC Maven plugin is no longer maintained.
