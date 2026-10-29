# date-parser: ten parsers, one language

The same date-schedule language implemented with **ANTLR 4 (Java and Python targets), JavaCC, GNU Bison, JFlex + CUP, SableCC,
Coco/R, Peggy (PEG), Lark (Earley and LALR) and Scala parser combinators**, all tested against one shared set of cases.
It is a hands-on comparison of LL, LALR, Earley, PEG and combinator parsing in Java, Scala, JavaScript and Python.

**[See the side-by-side comparison →](https://hjuergens.github.io/date-parser/)**

Beyond the shared example, the modules contain further grammars, for example
term structures such as `[3M,4M,6M]x(4M:1M:9M)`, date shifts such as `>Q` ("first day of next quarter"), and
natural-language rules such as "three days after next sunday".

Each `date-rule-*` module implements a grammar with a different technology. The modules are independent of each other.
Planned additions are in [TODO.md](TODO.md).

## Modules

| Module | Technology | Parser type | Language | What works | Tests |
|---|---|---|---|---|---|
| [date-rule-antlr](date-rule-antlr/README.md) | ANTLR 4.13 | ALL(*) (adaptive LL) | Java grammars, Joda-Time | **Most complete.** Periods (`14M7W9D`), shifts and selectors (`+3M1W`, `>>>wednesday`, `>Q`), term structures, lists, ISO and other date formats, iCalendar | 164 TestNG + 3 specs2 IT |
| [date-rule-combinators](date-rule-combinators/README.md) | Scala parser combinators 2.x | Recursive descent with backtracking | Scala 2.13, `java.time` | **Natural language:** "next thursday", "three weeks ago", "last monday in month", "2 London business days prior to the 3rd Wednesday …", seasons, business days | 155 JUnit/TestNG + 104 ScalaTest |
| [date-rule-coco](date-rule-coco/README.md) | Coco/R | LL(1) | Java | Date lists in ISO, German, and US notation → `LocalDate` | 92 TestNG |
| [date-rule-javacc](date-rule-javacc/README.md) | JavaCC (ParserGeneratorCC) | LL(k) | Java | Brace-matching demo grammar, JUnit 4 experiments | 97 JUnit (+7 ignored) |
| [date-rule-sablecc](date-rule-sablecc/README.md) | SableCC 2 | LALR(1) with typed AST | Java | Arithmetic expressions, AST walker and evaluator | 97 TestNG |
| [date-rule-cup-jflex](date-rule-cup-jflex/README.md) | JFlex 1.9 + CUP 11b | LALR(1) | Java | Pascal-subset lexer (its CUP grammar is a skeleton); complete CUP parser for the shared example | 93 TestNG |
| [date-rule-bison](date-rule-bison/README.md) | GNU Bison 3 (Java skeleton) | LALR(1) | Java | Infix calculator with error recovery. **Requires `bison`**; the module is empty without it | 94 TestNG |
| [date-rule-peg](date-rule-peg/README.md) | Peggy (PEG.js successor) | PEG (packrat) | JavaScript | `(start:step:end)` range lists | 91 `node:test` |
| [date-rule-lark](date-rule-lark/README.md) | Lark 1.3 | Earley and LALR(1), one grammar | Python | The shared example, every case with both algorithms | 172 pytest |
| [date-rule-antlr-python](date-rule-antlr-python/README.md) | ANTLR 4.13, Python target | ALL(*) (adaptive LL) | Python | The shared example; grammar without actions, plus a visitor | 86 pytest |

Each module also implements the [shared schedule example](#shared-example-schedule), so all ten implementations
can be compared on the same language. Each module README has a section on how its tool handles it.

## Shared example: schedule

> A visual version of this comparison, with the rules of all ten implementations side by side, is at [hjuergens.github.io/date-parser](https://hjuergens.github.io/date-parser/)
> (source: [docs/index.html](docs/index.html), served by GitHub Pages from `/docs`; regenerate it with `python3 docs/generate.py`).

A small date language that every tool can handle (no left recursion is needed, and all decisions are LL(1)):

```
2015-06-09                          one date (ISO)
09.06.2015 + 3M                     date plus a period (German notation)
06/09/2015 + 1W - 2D                periods applied left to right (US notation)
2015-06-12 + 1B                     business days: 2015-06-15, the Monday after
2015-06-09, 01.01.2000 + 1Y         a list of expressions
(2015-01-15 : 1Q : 2015-12-31)      range: start, step, end
2015-06-09 + 3M > IMM               next IMM date (third Wednesday of Mar/Jun/Sep/Dec)
2015-06-12 > SPOT USDCAD            FX spot date (T+1 for USD/CAD, otherwise T+2)
```

```
schedule = range | expr { "," expr } .
range    = "(" expr ":" period ":" expr ")" .
expr     = date { ("+" | "-") period | ">" ( "IMM" | "SPOT" [ PAIR ] ) } .   (* PAIR: six capital letters, e.g. EURUSD *)
date     = NUM ( "-" NUM "-" NUM | "." NUM "." NUM | "/" NUM "/" NUM ) .   (* ISO, German, US *)
period   = NUM ( "D" | "W" | "M" | "Q" | "Y" | "B" ) .                         (* B: business days *)
```

Periods follow `java.time`: months first (with the day clamped to the end of the month, so `2016-01-31 + 1M` is `2016-02-29`), then days.
`B` counts business days, Monday to Friday; `- 1B` counts backwards and `+ 0B` leaves even a Saturday as it is.
A range yields `start + k * step` while the date isn't after the end.
`> IMM` moves to the next IMM date strictly after the date (QuantLib's `IMM::nextDate` convention), so `2015-06-17 > IMM` is `2015-09-16`.
`> SPOT` moves to the FX spot date: `+ 1B` for USD against CAD, TRY, PHP or RUB, `+ 2B` for every other pair
or without a pair. Only weekends are skipped so far, there are no holiday calendars (`2015-12-31 > SPOT` is `2016-01-04`).

All implementations are tested against the same 86 cases in [examples/schedule.txt](examples/schedule.txt),
including invalid dates and syntax errors. Each module has a test that reads this file.

| Module | Grammar | Lines¹ | How values are computed | How errors surface | Notable |
|---|---|---|---|---|---|
| antlr | [DateSchedule.g4](date-rule-antlr/src/main/antlr4/io/github/hjuergens/time/DateSchedule.g4) | 42 | actions with `returns […]` | listener throws; `BailErrorStrategy` | `$start` is reserved, so range labels are `first`/`last`; an optional token in a loop needs its own rule |
| combinators | [ScheduleParsers.scala](date-rule-combinators/src/main/scala/de/juergens/text/ScheduleParsers.scala) | 71 | `^^` functions, `foldLeft` over the shifts | `NoSuccess` → exception | grammar is plain Scala; no generator |
| coco | [Schedule.atg](date-rule-coco/src/main/coco/Schedule.atg) | 64 | `(. … .)` actions, `out` parameters | `errors.count`, messages on a stream | recovery keeps running actions, so they must tolerate garbage |
| javacc | [Schedule.jj](date-rule-javacc/src/main/javacc/Schedule.jj) | 74 | Java code in productions | `ParseException`, `TokenMgrException` | locals declared per production; LL(1) without lookahead hints |
| sablecc | [schedule.grammar](date-rule-sablecc/src/main/sablecc/schedule.grammar) + [ScheduleEvaluator](date-rule-sablecc/src/main/java/de/juergens/schedule/ScheduleEvaluator.java) | 38 + 65 | separate evaluator over the typed AST | `ParserException`, `LexerException` | grammar without code; repeated elements need names |
| cup-jflex | [schedule.cup](date-rule-cup-jflex/src/main/cup/schedule.cup) + [schedule.jflex](date-rule-cup-jflex/src/main/jflex/schedule.jflex) | 36 + 35 | `{: … :}` actions, left recursion | `Exception` from `parse()` | `%cupsym` must precede `%cup` |
| bison | [schedule.y](date-rule-bison/src/main/resources/schedule.y) | 102² | `{ … }` actions, left recursion | `parse()` returns `false` | `$$` is untyped in Java; hand-written lexer |
| peg | [schedule.pegjs](date-rule-peg/schedule.pegjs) | 84³ | action blocks, ordered choice | `SyntaxError` | `range()` is built into Peggy actions; date arithmetic hand-written |
| lark | [schedule.lark](date-rule-lark/src/lark_schedule/schedule.lark) + [Transformer](date-rule-lark/src/lark_schedule/__init__.py) | 16 + 37 | `Transformer`, one method per rule alias | `UnexpectedInput`; `VisitError` unwrapped | the same grammar runs as Earley and as LALR(1); the contextual lexer accepts `>SPOTUSDCAD` |
| antlr-python | [DateSchedule.g4](date-rule-antlr-python/DateSchedule.g4) + [visitor](date-rule-antlr-python/src/antlr_schedule/__init__.py) | 29 + 43 | visitor over the parse tree | raising error listener | Java actions can't be generated into Python; `range` becomes `range_()` |

¹ Non-blank, non-comment lines. The JVM modules also share an 88-line `ScheduleSemantics` (periods, business days, IMM and spot dates, range expansion);
  `java.time.Period` can't express business days, so `B` is a small `TemporalAmount` of its own.
  The Python modules share a 50-line `semantics.py` (copied, like the Java helper).
² Including the hand-written scanner (about 55 lines).
³ Including JavaScript date arithmetic that reproduces `java.time` (about 50 lines, business days, IMM and spot dates included).

LALR tools (bison, CUP, SableCC) build the generators with conflict checking, and none of the three grammars has conflicts.
Coco/R's LL(1) check also passes without warnings.

## How it fits together

- [mise.toml](mise.toml) pins the toolchains (JDK, Maven, Node, Python, uv) and defines one task per module.
  Every module builds with its native tool: Maven for Java and Scala, npm for Peggy, uv for Python.
- The parent [pom.xml](pom.xml) manages versions for the Java and Scala modules (Java 17 release, Scala 2.13, TestNG, JUnit Platform, SLF4J, plugins).
- Each JVM module generates its parser in `generate-sources` into `target/generated-sources/<tool>`, then compiles and tests it.
  Generated code is never checked in; the ANTLR Python parser is generated by the task `generate:antlr-python`.
- Tests run with surefire (`mvn test`). Integration tests (`*Spec`, antlr only) run with failsafe in `mvn verify`.
- Surefire is pinned to 3.5.x because 3.6 no longer reads TestNG suite files, and the antlr module selects its tests in `testng.xml`.
- [repo/](repo) contains `jquantlib-0.2.4`, a test dependency of the combinators module. The parent build installs it into the local Maven repository during `validate`.

## Prerequisites

| Tool | Needed for |
|---|---|
| [mise](https://mise.jdx.dev/) | everything: `mise install` installs the pinned JDK 17, Maven, Node, Python and uv |
| `bison` 3.x on `/usr/bin/bison` (`sudo apt install bison`) | optional; activates [date-rule-bison](date-rule-bison/README.md) |

Without mise, install the tools from [mise.toml](mise.toml) yourself and run the commands of its tasks.

## Build and test

```
mise install                           # toolchains, as pinned in mise.toml
mise run test                          # all modules; the module tasks run in parallel
mise tasks                             # list the tasks, e.g. test:lark, test:peg, docs
mvn -pl date-rule-antlr test           # a single JVM module, directly with Maven
mvn -pl date-rule-bison -Pbison -Dbison.executable=/opt/bison/bin/bison verify   # bison outside /usr/bin
```

The first build downloads all dependencies; the JVM build then takes several minutes, mostly in the antlr and combinators modules.
Code coverage (JaCoCo) is written to `<module>/target/site/jacoco/`.

## License

[LICENSE.txt](LICENSE.txt) contains the Apache License 2.0. Note that [pom.xml](pom.xml) declares
LGPL 3.0; these two should be aligned.
