# date-rule-sablecc

Arithmetic expressions parsed with [SableCC](https://sablecc.org/) 2, an LALR(1) parser generator
that builds a strictly typed syntax tree and tree walkers (visitor pattern).

There is no date grammar yet: this module shows how SableCC works, as a basis for comparison.

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
There are 11 TestNG tests.

## Status

Working, but only arithmetic examples. The SableCC Maven plugin is no longer maintained.
