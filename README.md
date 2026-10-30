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
| [date-rule-antlr](date-rule-antlr/README.md) | ANTLR 4.13 | ALL(*) (adaptive LL) | Java grammars, Joda-Time | **Most complete.** Periods (`14M7W9D`), shifts and selectors (`+3M1W`, `>>>wednesday`, `>Q`), term structures, lists, ISO and other date formats, iCalendar | 266 TestNG + 3 specs2 IT |
| [date-rule-combinators](date-rule-combinators/README.md) | Scala parser combinators 2.x | Recursive descent with backtracking | Scala 2.13, `java.time` | **Natural language:** "next thursday", "three weeks ago", "last monday in month", "2 London business days prior to the 3rd Wednesday …", seasons, business days | 155 JUnit/TestNG + 206 ScalaTest |
| [date-rule-coco](date-rule-coco/README.md) | Coco/R | LL(1) | Java | Date lists in ISO, German, and US notation → `LocalDate` | 194 TestNG |
| [date-rule-javacc](date-rule-javacc/README.md) | JavaCC (ParserGeneratorCC) | LL(k) | Java | Brace-matching demo grammar, JUnit 4 experiments | 199 JUnit (+7 ignored) |
| [date-rule-sablecc](date-rule-sablecc/README.md) | SableCC 2 | LALR(1) with typed AST | Java | Arithmetic expressions, AST walker and evaluator | 199 TestNG |
| [date-rule-cup-jflex](date-rule-cup-jflex/README.md) | JFlex 1.9 + CUP 11b | LALR(1) | Java | Pascal-subset lexer (its CUP grammar is a skeleton); complete CUP parser for the shared example | 195 TestNG |
| [date-rule-bison](date-rule-bison/README.md) | GNU Bison 3 (Java skeleton) | LALR(1) | Java | Infix calculator with error recovery. **Requires `bison`**; the module is empty without it | 196 TestNG |
| [date-rule-peg](date-rule-peg/README.md) | Peggy (PEG.js successor) | PEG (packrat) | JavaScript | `(start:step:end)` range lists | 193 `node:test` |
| [date-rule-lark](date-rule-lark/README.md) | Lark 1.3 | Earley and LALR(1), one grammar | Python | The shared example, every case with both algorithms | 376 pytest |
| [date-rule-antlr-python](date-rule-antlr-python/README.md) | ANTLR 4.13, Python target | ALL(*) (adaptive LL) | Python | The shared example; grammar without actions, plus a visitor | 188 pytest |

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
T + 2B                              two business days after the base date, in the market of the evaluation
4 JUL {SAT - 1D, SUN + 1D}          Independence Day as observed by the NYSE, in the year of the base date
3 FRI MAR > PRECEDING               the third Friday of March, or the business day before if it's a holiday
T > NEXT 3 MON JAN                  the next Martin Luther King Day after the base date
SPOT + 1M > MODFOLLOWING            one month after spot, modified following
```

```
schedule = range | expr { "," expr } .
range    = "(" expr ":" period ":" expr ")" .
expr     = date { ("+" | "-") period
                | ">" ( "IMM" | "SPOT" [ PAIR ] | ROLL | ("NEXT" | "PREV") anchor )
                | "{" shift { "," shift } "}" } .                            (* PAIR: six capital letters, e.g. EURUSD *)
shift    = WEEKDAY ("+" | "-") period .                                      (* observance: {SAT - 1D, SUN + 1D} *)
anchor   = WEEKDAY | "BD" | "IMM" | annual .
date     = "T" | "TOM" | "SPOT" [ PAIR ]                                     (* base date, T + 1B, spot of T *)
         | NUM ( "-" NUM "-" NUM | "." NUM "." NUM | "/" NUM "/" NUM )       (* ISO, German, US *)
         | annual .
annual   = NUM MONTH | NUM WEEKDAY MONTH | "LAST" WEEKDAY MONTH | "EASTER" .  (* in the year of the base date *)
period   = NUM ( "D" | "W" | "M" | "Q" | "Y" | "B" ) .                         (* B: business days *)
ROLL     = "FOLLOWING" | "PRECEDING" | "MODFOLLOWING" | "BOM" | "EOM" | "BOY" | "EOY" .
MONTH    = "JAN" | … | "DEC" .   WEEKDAY = "MON" | … | "SUN" .
```

Periods follow `java.time`: months first (with the day clamped to the end of the month, so `2016-01-31 + 1M` is `2016-02-29`), then days.
`B` counts business days, Monday to Friday; `- 1B` counts backwards and `+ 0B` leaves even a Saturday as it is.
A range yields `start + k * step` while the date isn't after the end.
`> IMM` moves to the next IMM date strictly after the date (QuantLib's `IMM::nextDate` convention), so `2015-06-17 > IMM` is `2015-09-16`.
`> SPOT` moves to the FX spot date: `+ 1B` for USD against CAD, TRY, PHP or RUB, `+ 2B` for every other pair
or without a pair.

**A schedule is a rule, not a list of dates.** `parse(text)` returns a function of a *context*: the base date `T` and a *market*.
Both are only supplied when the rule is evaluated, and the grammar never names a market.
A market only answers questions about days: which days are business days, and so which day is spot.
The meaning of a rule doesn't depend on it (`IMM` is the third Wednesday everywhere).
Without a market only Saturdays and Sundays are skipped, so `2015-12-31 > SPOT` is `2016-01-04`, although 1 January is a holiday.
In Java, for example:

```java
Rule rule = DateSchedules.parse("T > SPOT USDCAD");
rule.evaluate(new Context(LocalDate.of(2015, 6, 9), ScheduleSemantics.withHolidays(Set.of(LocalDate.of(2015, 6, 10)))));
// [2015-06-11]: June 10 is a holiday in this market
```

**Holiday rules** describe a market's calendar with the rule types of a typical
holiday catalog: `4 JUL` (day of month), `3 MON JAN` (n-th weekday), `LAST MON MAY`
and `EASTER` (Western) are dates in the year of the base date; an observance `{SAT - 1D, SUN + 1D}` shifts a date by the first item for
its weekday (the items don't chain). Days or weekdays that don't exist in that year (`29 FEB` in 2025, `5 MON FEB`) are errors at evaluation.

| Catalog rule type | In this language |
|---|---|
| `fixed_gregorian` 7/4 | `4 JUL` |
| `nth_weekday` January, Monday, 3 | `3 MON JAN` |
| `last_weekday` May, Monday | `LAST MON MAY` |
| `easter_offset` −2 | `EASTER - 2D` |
| `observance_shift` {6: −1, 7: 1} | `4 JUL {SAT - 1D, SUN + 1D}` |
| `relative_to_event` Thanksgiving + 1 | `4 THU NOV + 1D` |
| `nth_weekday_prev_business` | `3 FRI MAR > PRECEDING` |
| `last_business_day` February | `1 MAR - 1B` |

**Rolling**, seen from the base date: `TOM` is `T + 1B` and `SPOT [PAIR]` is `T > SPOT [PAIR]`. `> NEXT x` and `> PREV x` move to the
first `x` strictly after or before the date, where `x` is a weekday, `BD` (business day), `IMM` or an annual date, so
`T > NEXT 4 JUL` is this year's or next year's Independence Day. `> BOM`, `> EOM`, `> BOY` and `> EOY` move to the first or last day of
the month or year; `> FOLLOWING`, `> PRECEDING` and `> MODFOLLOWING` (following unless that leaves the month) move to a business day of
the market. Lunar calendars (Hijri, Hebrew, Chinese) are planned, see [docs/plans/holiday-rules.md](docs/plans/holiday-rules.md).

All implementations are tested against the same 188 cases in [examples/schedule.txt](examples/schedule.txt),
including invalid dates and syntax errors. Each module has a test that reads this file. Cases that need a base date or a
market name them in a prefix, `[2015-06-09 XTEST] T + 2B => 2015-06-12`, and test markets are declared as data
(`market XTEST = 2015-06-10, …`: Saturdays, Sundays and these holidays). The right-hand side is itself a schedule, parsed by the
implementation under test and evaluated per year, so `market XNYS = 1 JAN {SUN + 1D}, 3 MON JAN, …` declares the NYSE calendar from
holiday rules (`ScheduleSemantics.withHolidayRules`).

Keywords are reserved: `EASTER` has six capital letters like a currency pair, and `T > SPOT EASTER` is an error everywhere.
The tools with a lexer get this from their rule order; Peggy, the Scala combinators and Lark's contextual lexer exclude the keyword in the pair pattern.

| Module | Grammar | Lines¹ | How the rule is built | How errors surface | Notable |
|---|---|---|---|---|---|
| antlr | [DateSchedule.g4](date-rule-antlr/src/main/antlr4/io/github/hjuergens/time/DateSchedule.g4) | 80 | actions with `returns […]` build functions | listener throws; `BailErrorStrategy` | `$start` is reserved, so range labels are `first`/`last`; an attribute `rule` becomes `rule_`; an optional token in a loop needs its own rule |
| combinators | [ScheduleParsers.scala](date-rule-combinators/src/main/scala/de/juergens/text/ScheduleParsers.scala) | 155 | `^^` functions of the context, `foldLeft` over the shifts | `NoSuccess` → exception | grammar is plain Scala; no generator |
| coco | [Schedule.atg](date-rule-coco/src/main/coco/Schedule.atg) | 116 | `(. … .)` actions build functions, `out` parameters | `errors.count`, messages on a stream | recovery keeps running actions, so they must tolerate garbage (now they only build functions); an attribute whose type contains `>` needs `<. … .>`, and the `out` attribute comes first |
| javacc | [Schedule.jj](date-rule-javacc/src/main/javacc/Schedule.jj) | 127 | productions return functions | `ParseException`, `TokenMgrException` | locals declared per production; LL(1) without lookahead hints |
| sablecc | [schedule.grammar](date-rule-sablecc/src/main/sablecc/schedule.grammar) + [ScheduleEvaluator](date-rule-sablecc/src/main/java/de/juergens/schedule/ScheduleEvaluator.java) | 72 + 116 | the typed AST is the rule; an evaluator walks it with the context | `ParserException`, `LexerException` | grammar without code; repeated elements need names |
| cup-jflex | [schedule.cup](date-rule-cup-jflex/src/main/cup/schedule.cup) + [schedule.jflex](date-rule-cup-jflex/src/main/jflex/schedule.jflex) | 66 + 50 | `{: … :}` actions build functions, left recursion | `Exception` from `parse()` | `%cupsym` must precede `%cup` |
| bison | [schedule.y](date-rule-bison/src/main/resources/schedule.y) | 170² | `{ … }` actions build functions, left recursion | `parse()` returns `false` | `$$` is untyped in Java; hand-written lexer |
| peg | [schedule.pegjs](date-rule-peg/schedule.pegjs) | 190³ | action blocks return functions, ordered choice | `SyntaxError` | `range()` is built into Peggy actions; date arithmetic hand-written |
| lark | [schedule.lark](date-rule-lark/src/lark_schedule/schedule.lark) + [Transformer](date-rule-lark/src/lark_schedule/__init__.py) | 37 + 79 | `Transformer` returns closures, one method per rule alias | `UnexpectedInput`; `VisitError` unwrapped | the same grammar runs as Earley and as LALR(1); the contextual lexer accepts `>SPOTUSDCAD` |
| antlr-python | [DateSchedule.g4](date-rule-antlr-python/DateSchedule.g4) + [visitor](date-rule-antlr-python/src/antlr_schedule/__init__.py) | 55 + 93 | visitor over the parse tree returns closures | raising error listener | Java actions can't be generated into Python; `range` becomes `range_()` |

¹ Non-blank, non-comment lines. The JVM modules also share a 261-line `ScheduleSemantics` (market, context, rule builders, periods,
  business days, IMM and spot dates, ranges, annual dates, rolling); `java.time.Period` can't express business days, so `B` is a small
  `TemporalAmount` of its own. The Python modules share a 166-line `semantics.py` (copied, like the Java helper).
² Including the hand-written scanner (about 70 lines).
³ Including JavaScript date arithmetic that reproduces `java.time` (about 140 lines with markets, business days, IMM and spot dates,
  annual dates and rolling).

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
- [.mvn/settings.xml](.mvn/settings.xml) (passed by [.mvn/maven.config](.mvn/maven.config)) redirects the repositories of the
  shut-down maven.java.net, which old parent POMs of plugin dependencies still declare, to Maven Central.
- [repo/](repo) contains `jquantlib-0.2.4`, a test dependency of the combinators module. The parent build installs it into the local Maven repository during `validate`.

## Prerequisites

| Tool | Needed for |
|---|---|
| [mise](https://mise.jdx.dev/) | everything: `mise install` installs the pinned JDK 17, Maven, Node, Python and uv |
| `bison` 3.x on `/usr/bin/bison` (`sudo apt install bison`) | optional; activates [date-rule-bison](date-rule-bison/README.md) |

Without mise, install the tools from [mise.toml](mise.toml) yourself and run the commands of its tasks.

### Dev container

[.devcontainer/](.devcontainer) needs only Docker: the image adds `bison` and mise to a Debian base, and creating the
container runs `mise install`, so **every** module, bison included, builds without installing anything on the host.
In VS Code, run "Dev Containers: Reopen in Container", then `mise run test` in its terminal.
Maven, mise and uv downloads are kept in Docker volumes (`date-parser-*`) and survive rebuilds of the container;
the Python virtual environments of the container are volumes as well, separate from the host's `.venv`.

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
