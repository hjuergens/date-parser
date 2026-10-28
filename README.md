# date-parser: eight parser generators, one language

The same date-schedule language implemented with **ANTLR 4, JavaCC, GNU Bison, JFlex + CUP, SableCC, Coco/R, Peggy (PEG)
and Scala parser combinators**, all tested against one shared set of cases. It is a hands-on comparison of LL, LALR, PEG
and combinator parsing in Java, Scala and JavaScript.

**[See the side-by-side comparison →](https://hjuergens.github.io/date-parser/)**

Beyond the shared example, the modules contain further grammars, for example
term structures such as `[3M,4M,6M]x(4M:1M:9M)`, date shifts such as `>Q` ("first day of next quarter"), and
natural-language rules such as "three days after next sunday".

Each `date-rule-*` module implements a grammar with a different technology. The modules are independent of each other.

## Modules

| Module | Technology | Parser type | Language | What works | Tests |
|---|---|---|---|---|---|
| [date-rule-antlr](date-rule-antlr/README.md) | ANTLR 4.13 | ALL(*) (adaptive LL) | Java grammars, Joda-Time | **Most complete.** Periods (`14M7W9D`), shifts and selectors (`+3M1W`, `>>>wednesday`, `>Q`), term structures, lists, ISO and other date formats, iCalendar | 114 TestNG + 3 specs2 IT |
| [date-rule-combinators](date-rule-combinators/README.md) | Scala parser combinators 2.x | Recursive descent with backtracking | Scala 2.13, `java.time` | **Natural language:** "next thursday", "three weeks ago", "last monday in month", "2 London business days prior to the 3rd Wednesday …", seasons, business days | 155 JUnit/TestNG + 54 ScalaTest |
| [date-rule-coco](date-rule-coco/README.md) | Coco/R | LL(1) | Java | Date lists in ISO, German, and US notation → `LocalDate` | 42 TestNG |
| [date-rule-javacc](date-rule-javacc/README.md) | JavaCC (ParserGeneratorCC) | LL(k) | Java | Brace-matching demo grammar, JUnit 4 experiments | 47 JUnit (+7 ignored) |
| [date-rule-sablecc](date-rule-sablecc/README.md) | SableCC 2 | LALR(1) with typed AST | Java | Arithmetic expressions, AST walker and evaluator | 47 TestNG |
| [date-rule-cup-jflex](date-rule-cup-jflex/README.md) | JFlex 1.9 + CUP 11b | LALR(1) (lexer only so far) | Java | Pascal-subset lexer; the CUP grammar is a skeleton | 43 TestNG |
| [date-rule-bison](date-rule-bison/README.md) | GNU Bison 3 (Java skeleton) | LALR(1) | Java | Infix calculator with error recovery. **Requires `bison`**; the module is empty without it | 44 TestNG |
| [date-rule-peg](date-rule-peg/README.md) | Peggy (PEG.js successor) | PEG (packrat) | JavaScript | `(start:step:end)` range lists | 41 `node:test` |

Each module also implements the [shared schedule example](#shared-example-schedule), so all eight tools
can be compared on the same language.

## Shared example: schedule

> A visual version of this comparison, with the rules of all eight tools side by side, is at [hjuergens.github.io/date-parser](https://hjuergens.github.io/date-parser/)
> (source: [docs/index.html](docs/index.html), served by GitHub Pages from `/docs`; regenerate it with `python3 docs/generate.py`).

A small date language that every tool can handle (no left recursion is needed, and all decisions are LL(1)):

```
2015-06-09                          one date (ISO)
09.06.2015 + 3M                     date plus a period (German notation)
06/09/2015 + 1W - 2D                periods applied left to right (US notation)
2015-06-09, 01.01.2000 + 1Y         a list of expressions
(2015-01-15 : 1Q : 2015-12-31)      range: start, step, end
```

```
schedule = range | expr { "," expr } .
range    = "(" expr ":" period ":" expr ")" .
expr     = date { ("+" | "-") period } .
date     = NUM ( "-" NUM "-" NUM | "." NUM "." NUM | "/" NUM "/" NUM ) .   (* ISO, German, US *)
period   = NUM ( "D" | "W" | "M" | "Q" | "Y" ) .
```

Periods follow `java.time`: months first (with the day clamped to the end of the month, so `2016-01-31 + 1M` is `2016-02-29`), then days.
A range yields `start + k * step` while the date isn't after the end.

All implementations are tested against the same 36 cases in [examples/schedule.txt](examples/schedule.txt),
including invalid dates and syntax errors. Each module has a test that reads this file.

| Module | Grammar | Lines¹ | How values are computed | How errors surface | Notable |
|---|---|---|---|---|---|
| antlr | [DateSchedule.g4](date-rule-antlr/src/main/antlr4/io/github/hjuergens/time/DateSchedule.g4) | 35 | actions with `returns […]` | listener throws; `BailErrorStrategy` | `$start` is reserved, so range labels are `first`/`last` |
| combinators | [ScheduleParsers.scala](date-rule-combinators/src/main/scala/de/juergens/text/ScheduleParsers.scala) | 39 | `^^` functions, `foldLeft` over the shifts | `NoSuccess` → exception | grammar is plain Scala; no generator |
| coco | [Schedule.atg](date-rule-coco/src/main/coco/Schedule.atg) | 54 | `(. … .)` actions, `out` parameters | `errors.count`, messages on a stream | recovery keeps running actions, so they must tolerate garbage |
| javacc | [Schedule.jj](date-rule-javacc/src/main/javacc/Schedule.jj) | 69 | Java code in productions | `ParseException`, `TokenMgrException` | locals declared per production; LL(1) without lookahead hints |
| sablecc | [schedule.grammar](date-rule-sablecc/src/main/sablecc/schedule.grammar) + [ScheduleEvaluator](date-rule-sablecc/src/main/java/de/juergens/schedule/ScheduleEvaluator.java) | 31 + 61 | separate evaluator over the typed AST | `ParserException`, `LexerException` | grammar without code; repeated elements need names |
| cup-jflex | [schedule.cup](date-rule-cup-jflex/src/main/cup/schedule.cup) + [schedule.jflex](date-rule-cup-jflex/src/main/jflex/schedule.jflex) | 33 + 31 | `{: … :}` actions, left recursion | `Exception` from `parse()` | `%cupsym` must precede `%cup` |
| bison | [schedule.y](date-rule-bison/src/main/resources/schedule.y) | 84² | `{ … }` actions, left recursion | `parse()` returns `false` | `$$` is untyped in Java; hand-written lexer |
| peg | [schedule.pegjs](date-rule-peg/schedule.pegjs) | 60³ | action blocks, ordered choice | `SyntaxError` | `range()` is built into Peggy actions; date arithmetic hand-written |

¹ Non-blank, non-comment lines. The JVM modules also share a 30-line `ScheduleSemantics` (periods and range expansion).
² Including the hand-written scanner (about 45 lines).
³ Including JavaScript date arithmetic that reproduces `java.time` (about 35 lines).

LALR tools (bison, CUP, SableCC) build the generators with conflict checking, and none of the three grammars has conflicts.
Coco/R's LL(1) check also passes without warnings.

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
