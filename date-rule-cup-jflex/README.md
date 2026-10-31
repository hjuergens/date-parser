# date-rule-cup-jflex

A lexer built with [JFlex](https://jflex.de/) and token definitions built with [CUP](https://www2.cs.tum.edu/projects/cup/),
the classic LALR(1) pair for Java (lex/yacc style).

There is no date grammar yet: the example is a lexer for a Pascal subset.

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
- There are 7 TestNG tests in [PascalLexerTest](src/test/java/io/github/hjuergens/PascalLexerTest.java).

CUP warns that the terminals are "declared but never used". That's expected while the grammar has no rules.

## Status

The lexer works. The CUP grammar is a skeleton: it has no productions apart from a dummy rule.
