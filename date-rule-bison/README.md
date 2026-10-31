# date-rule-bison

An infix calculator built with [GNU Bison](https://www.gnu.org/software/bison/)'s Java skeleton (LALR(1)),
including error recovery.

There is no date grammar yet.

## What it parses

[calc.y](src/main/resources/calc.y) reads one expression per line and collects the values in `Calc.results`:

| Input | Value |
|---|---|
| `1+2*3` | 7 |
| `(1+2)*3` | 9 |
| `2^3^2` | 512 (`^` is right associative) |
| `-2*3` | -6 |
| `1=2` | error: `calc: error: 1 != 2` |

A syntax error discards the rest of the line (`error '\n'`), and parsing continues with the next line.

## Usage

```java
CalcLexer lexer = new CalcLexer(System.in);   // package io.github.hjuergens.bison
Calc calc = new Calc(lexer);
calc.parse();
calc.results;   // one Integer per valid line
```

## Build and test

You need the `bison` executable; **without it, the module builds empty and has no tests**.

```
sudo apt install bison                     # profile 'bison' activates when /usr/bin/bison exists
mvn -pl date-rule-bison verify

mvn -pl date-rule-bison -Pbison -Dbison.executable=/path/to/bison verify   # bison installed elsewhere
```

With the profile active, `maven-antrun-plugin` runs bison in `generate-sources` and writes
`target/generated-sources/bison/io/github/hjuergens/bison/Calc.java`. The tests in
[src/bison-test/java](src/bison-test/java) are only compiled then (8 TestNG tests).

The Windows binaries in [tools/win_flex_bison-latest](tools/win_flex_bison-latest) are an old bundled copy and aren't used by the build.

## Status

Works with bison 3.8. The grammar was updated from the bison 2.x syntax:
the `api.*` directives, a package, and the token constants from the `Lexer` interface replace hard-coded token numbers.
