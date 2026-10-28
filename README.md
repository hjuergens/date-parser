# date-parser

A sandbox for comparing parser technologies on the JVM (and one in JavaScript), using date expressions as the common theme:
term structures such as `[3M,4M,6M]x(4M:1M:9M)`, date shifts such as `>Q` ("first day of next quarter"), and
natural-language rules such as "three days after next sunday".

Each `date-rule-*` module implements a grammar with a different technology. The modules are independent of each other.

## Modules

| Module | Technology | Parser type | Language | What works | Tests |
|---|---|---|---|---|---|
| [date-rule-antlr](date-rule-antlr/README.md) | ANTLR 4.13 | ALL(*) (adaptive LL) | Java grammars, Joda-Time | **Most complete.** Periods (`14M7W9D`), shifts and selectors (`+3M1W`, `>>>wednesday`, `>Q`), term structures, lists, ISO and other date formats, iCalendar | 78 TestNG + 3 specs2 IT |
| [date-rule-combinators](date-rule-combinators/README.md) | Scala parser combinators 2.x | Recursive descent with backtracking | Scala 2.13, `java.time` | **Natural language:** "next thursday", "three weeks ago", "last monday in month", "2 London business days prior to the 3rd Wednesday …", seasons, business days | 155 JUnit/TestNG + 18 ScalaTest |
| [date-rule-coco](date-rule-coco/README.md) | Coco/R | LL(1) | Java | Date lists in ISO, German, and US notation → `LocalDate` | 6 TestNG |
| [date-rule-javacc](date-rule-javacc/README.md) | JavaCC (ParserGeneratorCC) | LL(k) | Java | Brace-matching demo grammar, JUnit 4 experiments | 11 JUnit (+7 ignored) |
| [date-rule-sablecc](date-rule-sablecc/README.md) | SableCC 2 | LALR(1) with typed AST | Java | Arithmetic expressions, AST walker and evaluator | 11 TestNG |
| [date-rule-cup-jflex](date-rule-cup-jflex/README.md) | JFlex 1.9 + CUP 11b | LALR(1) (lexer only so far) | Java | Pascal-subset lexer; the CUP grammar is a skeleton | 7 TestNG |
| [date-rule-bison](date-rule-bison/README.md) | GNU Bison 3 (Java skeleton) | LALR(1) | Java | Infix calculator with error recovery. **Requires `bison`**; the module is empty without it | 8 TestNG |
| [date-rule-peg](date-rule-peg/README.md) | Peggy (PEG.js successor) | PEG (packrat) | JavaScript | `(start:step:end)` range lists | 5 `node:test` |

Only antlr, combinators, and coco parse dates so far. The other modules show their tool with a small sample grammar.

## How it fits together

- The parent [pom.xml](pom.xml) manages versions for all modules (Java 17 release, Scala 2.13, TestNG, JUnit Platform, SLF4J, plugins).
- Each module generates its parser in `generate-sources` into `target/generated-sources/<tool>`, then compiles and tests it.
  Generated code is never checked in.
- Tests run with surefire (`mvn test`). Integration tests (`*Spec`, antlr only) run with failsafe in `mvn verify`.
- Surefire is pinned to 3.5.x because 3.6 no longer reads TestNG suite files, and the antlr module selects its tests in `testng.xml`.
- [repo/](repo) contains `jquantlib-0.2.4`, a test dependency of the combinators module. The parent build installs it into the local Maven repository during `validate`.

## Prerequisites

| Tool | Needed for |
|---|---|
| JDK 17 or later | everything |
| Maven 3.9 or later | everything |
| `bison` 3.x on `/usr/bin/bison` (`sudo apt install bison`) | optional; activates [date-rule-bison](date-rule-bison/README.md) |
| Node.js | not needed: the peg module downloads Node 24 into its `target/` |

## Build and test

```
mvn clean verify                       # all modules, unit and integration tests
mvn -pl date-rule-antlr test           # a single module
mvn -pl date-rule-bison -Pbison -Dbison.executable=/opt/bison/bin/bison verify   # bison outside /usr/bin
```

The first build downloads all dependencies and Node.js; a full build then takes about 3 minutes.
Code coverage (JaCoCo) is written to `<module>/target/site/jacoco/`.

## License

[LICENSE.txt](LICENSE.txt) contains the Apache License 2.0. Note that [pom.xml](pom.xml) declares
LGPL 3.0; these two should be aligned.
