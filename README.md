# date-parser: twelve parsers, one language

The same date-schedule language implemented with **ANTLR 4 (Java and Python targets), JavaCC, GNU Bison (Java and C, with Flex),
JFlex + CUP, SableCC, Coco/R, Peggy (PEG), Lark (Earley and LALR), instaparse (GLL, Clojure) and Scala parser combinators**, all
tested against one shared set of cases. It is a hands-on comparison of LL, LALR, Earley, GLL, PEG and combinator parsing in Java,
Scala, JavaScript, Python, Clojure and C.

**[See the side-by-side comparison →](https://hjuergens.github.io/date-parser/)**

Beyond the shared example, the modules contain further grammars, for example
term structures such as `[3M,4M,6M]x(4M:1M:9M)`, date shifts such as `>Q` ("first day of next quarter"), and
natural-language rules such as "three days after next sunday".

Each `date-rule-*` module implements a grammar with a different technology. The modules are independent of each other.
Planned additions are in [TODO.md](TODO.md).

## Modules

| Module | Technology | Parser type | Language | What works | Tests |
|---|---|---|---|---|---|
| [date-rule-antlr-java](date-rule-antlr-java/README.md) | ANTLR 4.13 | ALL(*) (adaptive LL) | Java grammars, Joda-Time | **Most complete.** Periods (`14M7W9D`), shifts and selectors (`+3M1W`, `>>>wednesday`, `>Q`), term structures, lists, ISO and other date formats, iCalendar | 6038 TestNG + 3 specs2 IT |
| [date-rule-combinators](date-rule-combinators/README.md) | Scala parser combinators 2.x | Recursive descent with backtracking | Scala 2.13, `java.time` | **Natural language:** "next thursday", "three weeks ago", "last monday in month", "2 London business days prior to the 3rd Wednesday …", seasons, business days | 158 JUnit/TestNG + 5978 ScalaTest |
| [date-rule-coco](date-rule-coco/README.md) | Coco/R | LL(1) | Java | Date lists in ISO, German, and US notation → `LocalDate` | 5966 TestNG |
| [date-rule-javacc](date-rule-javacc/README.md) | JavaCC (ParserGeneratorCC) | LL(k) | Java | Brace-matching demo grammar, JUnit 4 experiments | 5971 JUnit (+5 ignored) |
| [date-rule-sablecc](date-rule-sablecc/README.md) | SableCC 2 | LALR(1) with typed AST | Java | Arithmetic expressions, AST walker and evaluator | 5971 TestNG |
| [date-rule-cup-jflex](date-rule-cup-jflex/README.md) | JFlex 1.9 + CUP 11b | LALR(1) | Java | Pascal-subset lexer (its CUP grammar is a skeleton); complete CUP parser for the shared example | 5967 TestNG |
| [date-rule-bison](date-rule-bison/README.md) | GNU Bison 3 (Java skeleton) | LALR(1) | Java | Infix calculator with error recovery. **Requires `bison`**; the module is empty without it | 5968 TestNG |
| [date-rule-flex-bison](date-rule-flex-bison/README.md) | Flex 2.6 + GNU Bison 3.8 (C skeleton) | LALR(1) | C11, Go via SWIG | The shared example; reentrant scanner and pure parser, the actions build a syntax tree that a C evaluator walks. A SWIG binding runs it from Go. Builds in Docker | 5960 cases + 5960 Go subtests |
| [date-rule-peg](date-rule-peg/README.md) | Peggy (PEG.js successor) | PEG (packrat) | JavaScript | `(start:step:end)` range lists | 5965 `node:test` |
| [date-rule-lark](date-rule-lark/README.md) | Lark 1.3 | Earley and LALR(1), one grammar | Python | The shared example, every case with both algorithms | 11920 pytest |
| [date-rule-antlr-python](date-rule-antlr-python/README.md) | ANTLR 4.13, Python target | ALL(*) (adaptive LL) | Python | The shared example; grammar without actions, plus a visitor | 5960 pytest |
| [date-rule-instaparse](date-rule-instaparse/README.md) | instaparse 1.5 | GLL (any context-free grammar) | Clojure 1.12 | The shared example; EBNF grammar read at run time, `insta/transform` onto the shared Java helper | 5960 TestNG |

Beside the parser modules, [date-rule-runtime](date-rule-runtime/README.md) changes rules at run time: a rules file in the
schedule language (`rule @IMMH = T > NEXT 3 WED MAR`, `assert @IMMH [2026-11-02 @XNYS] => 2027-03-17`), read by the same ANTLR grammar, is validated, swapped
atomically and reloaded when it is saved, in Java and without HotSwap or JVMTI.

Each module also implements the [shared schedule example](#shared-example-schedule), so all twelve implementations
can be compared on the same language. Each module README has a section on how its tool handles it.

## Shared example: schedule

> A visual version of this comparison, with the rules of all twelve implementations side by side, is at [hjuergens.github.io/date-parser](https://hjuergens.github.io/date-parser/)
> (source: [docs/index.html](docs/index.html), served by GitHub Pages from `/docs`; regenerate it with `python3 docs/generate.py`).
> Its playground runs the Peggy parser in the browser and draws the syntax tree of each input; the tree comes from Peggy's
> tracer, so the grammar needs no change for it, and its bracket notation can be pasted into a syntax tree generator.

A small date language that every tool can handle (no left recursion is needed, and all decisions are LL(1)):

```
2015-06-09                          one date (ISO)
09.06.2015 + 3M                     date plus a period (German notation)
06/09/2015 + 1W - 2D                periods applied left to right (US notation)
2015-06-12 + 1B                     business days: 2015-06-15, the Monday after
2015-06-09, 01.01.2000 + 1Y         a list of expressions
[2015-01-15 : 1Q : 2015-12-31]      series: start, step, end
[2026-01-30 : 2026-02-02[           every day, the end left out ("]" at the start leaves the start out)
[2026-01-01 : 3M : 2026-12-31] > EOQ > 3RD WED IN MONTH   shifts apply to each element: the IMM dates of 2026
[T : T + 7D] \ {1 JAN, 6 JAN} & 25 DEC   exclude and include: sorted, without duplicates
[T : 1Y : T + 2Y] > {1 FEB, 1 MAY}  dates in year: 1 February and 1 May of three years
2015-06-09 + 3M > IMM               next IMM date (third Wednesday of Mar/Jun/Sep/Dec)
2015-06-12 > SPOT USDCAD            FX spot date (T+1 for USD/CAD, otherwise T+2)
T + 2B                              two business days after the base date, in the market of the evaluation
4 JUL {SAT - 1D, SUN + 1D}          Independence Day as observed by the NYSE, in the year of the base date
3 FRI MAR > PRECEDING               the third Friday of March, or the business day before if it's a holiday
T > NEXT 3 MON JAN                  the next Martin Luther King Day after the base date
T > NEXT 7TH {MON, FRI}             the seventh Monday or Friday after the base date
SPOT > EOQ > IMM                    the IMM date of the quarter after spot's
T > 3RD WED IN MONTH                the third Wednesday in the month of the base date
T > 2ND LAST BD IN MONTH            the second-to-last business day of that month
SPOT + 1M > MODFOLLOWING            one month after spot, modified following
24 DEC IF MON TUE WED THU           Christmas Eve, but only from Monday to Thursday (no date otherwise)
1 SHAWWAL, 10 TISHRI, 1 ZHENGYUE    Eid al-Fitr, Yom Kippur, Chinese New Year: lunar months in the base date's year
@FOMC + 1B                          the business day after each FOMC decision of that year (a named event)
EQUINOX MAR UTC+9, SOLARTERM 5 UTC+8  Japan's Vernal Equinox Day, Qingming: the day the sun reaches 0 and 15 degrees
```

```
schedule = item { "," item } .
item     = expr { combine } [ "IF" ( WEEKDAY { WEEKDAY } | "{" WEEKDAY { "," WEEKDAY } "}" ) ] .   (* no date on other weekdays *)
combine  = ( "&" | "\\" ) operand { move } .                           (* include, exclude; sorted, no duplicates *)
operand  = date | "{" expr { "," expr } "}" .
series   = ( "[" | "]" ) expr ":" [ period ":" ] expr ( "]" | "[" ) .        (* "]a" and "b[" leave out the endpoint *)
expr     = date { move } .                                                 (* each move applies to everything on its left *)
move     = ("+" | "-") period
         | ">" ( "IMM" | "SPOT" [ PAIR ] | ROLL | ("NEXT" | "PREV") [ ORDINAL ] anchor
               | count select "IN" PERIOD                                  (* > 3RD WED IN MONTH *)
               | "{" annual { "," annual } "}" )                           (* dates in year: > {1 FEB, 1 MAY} *)
         | "{" shift { "," shift } "}" .                                   (* PAIR: six capital letters, e.g. EURUSD *)
shift    = WEEKDAY ("+" | "-") period .                                      (* observance: {SAT - 1D, SUN + 1D} *)
anchor   = target | "{" target { "," target } "}" .                    (* NEXT {MON, FRI}: the nearest *)
target   = chosen | "LAST" WEEKDAY MONTH .
count    = ORDINAL [ "LAST" ] | "LAST" .                                      (* 3RD, 2ND LAST, LAST *)
select   = chosen | "{" target { "," target } "}" .                    (* LAST MON MAY only in braces *)
chosen   = WEEKDAY | "BD" | "IMM" | fixed | EVENT .
date     = "T" | "TOM" | "SPOT" [ PAIR ]                                     (* base date, T + 1B, spot of T *)
         | NUM ( "-" NUM "-" NUM | "." NUM "." NUM | "/" NUM "/" NUM )       (* ISO, German, US *)
         | annual | EVENT | series .                                     (* EVENT, series: one value per date *)
annual   = fixed | "LAST" WEEKDAY MONTH .                                   (* in the year of the base date *)
fixed    = NUM MONTH | NUM WEEKDAY MONTH | "EASTER"
         | ("EQUINOX" | "SOLSTICE") MONTH [ ZONE ] | "SOLARTERM" NUM [ ZONE ] .
period   = NUM ( "D" | "W" | "M" | "Q" | "H" | "Y" | "B" ) .                   (* H: half year; B: business days *)
ROLL     = "FOLLOWING" | "PRECEDING" | "MODFOLLOWING" | "BOW" | "EOW" | "BOM" | "EOM" | "BOQ" | "EOQ"
         | "BOH" | "EOH" | "BOY" | "EOY" .                                   (* BOW, EOW: Monday, Sunday *)
PERIOD   = "WEEK" | "MONTH" | "QUARTER" | "HALFYEAR" | "YEAR" .
MONTH    = "JAN" | … | "DEC"                                                (* Gregorian *)
         | "MUHARRAM" | … | "SHAWWAL" | … | "DHULHIJJAH"                     (* Hijri, Umm al-Qura *)
         | "NISAN" | … | "TISHRI" | … | "ADAR" | "ADARII"                    (* Hebrew *)
         | "ZHENGYUE" | "ERYUE" | … | "LAYUE" .                             (* Chinese *)
WEEKDAY  = "MON" | … | "SUN" .
EVENT    = "@" UPPER { UPPER | DIGIT | "_" } .                              (* a named event such as @FOMC *)
ZONE     = "UTC" ("+" | "-") NUM .
ORDINAL  = NUM ( "ST" | "ND" | "RD" | "TH" ) .                              (* one token: 3RD *)                                         (* one token: UTC+9 *)
```

**In grammatical terms** (after [grammatical category](https://en.wikipedia.org/wiki/Grammatical_category)): the upper-case symbols
are *lexical* categories, the word classes `NUM`, `MONTH`, `WEEKDAY`, `ROLL`, `PAIR` and the keywords, which the lexer recognises;
the lower-case rules are *phrasal* categories (`date`, `period`, `expr`, `schedule`), which the parser builds from words.
The numbers carry the content, and the grammar adds a few things with a small, closed set of forms, as a natural language does:

- *number*: an `expr` is one date; a list or a range is several.
- *tense*: `T` is the base date, the anchor date that tense relates to; `TOM` and `SPOT` are relative to it, like
  "tomorrow"; `> NEXT` and `> PREV` look forward and back from a date.
- *definiteness*: `4 JUL` is definite through its context, the 4 July in the year of `T`, as "the dog" is the one already
  mentioned; `T > NEXT 4 JUL` picks the next one instead.

As with an inflection, a category the language has must be marked: a period always carries its unit (`3M`, never `3`).

Periods follow `java.time`: months first (with the day clamped to the end of the month, so `2016-01-31 + 1M` is `2016-02-29`), then days.
`B` counts business days, Monday to Friday; `- 1B` counts backwards and `+ 0B` leaves even a Saturday as it is.
A series yields `start + k * step` while the date isn't after the end, every day without a step; like an event it is a date
with several values, so the shifts after it apply to each element.
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
Months of the Hijri (Umm al-Qura), Hebrew and Chinese calendars are month names too: `1 SHAWWAL` is the day in the Gregorian year of the
base date, and an error if it falls there twice (a Hijri date does about every 33 years) or not at all. `ADAR` is Adar I in a Hebrew leap
year, `ADARII` Adar II; Chinese leap months have no name. `24 DEC IF MON TUE WED THU` keeps the date only on these weekdays: a list item
without a date is left out, so a rule can apply in some years and not in others.
`EQUINOX MAR`, `SOLSTICE JUN`, `EQUINOX SEP` and `SOLSTICE DEC` are the days on which the sun's apparent longitude reaches 0, 90,
180 and 270 degrees; `SOLARTERM 1` to `SOLARTERM 24` are the Chinese solar terms, counted from Lichun at 315 degrees, so
`SOLARTERM 5` is Qingming (15 degrees). The day is the one in UTC, or in the offset written after it: Japan's equinox days are
`EQUINOX MAR UTC+9` and `EQUINOX SEP UTC+9`. The offset is part of the rule, not of the market, as the meaning of a rule doesn't
depend on the market. Every module computes the instant itself after Jean Meeus, *Astronomical Algorithms* (chapters 25 and
27, with the 44 largest longitude terms of VSOP87 from appendix III and ΔT after Espenak and Meeus): within about a minute of the
published instants, so a date can only differ when the event is that close to midnight.

| Catalog rule type | In this language |
|---|---|
| `fixed_gregorian` 7/4 | `4 JUL` |
| `nth_weekday` January, Monday, 3 | `3 MON JAN` |
| `last_weekday` May, Monday | `LAST MON MAY` |
| `easter_offset` −2 | `EASTER - 2D` |
| `observance_shift` {6: −1, 7: 1} | `4 JUL {SAT - 1D, SUN + 1D}` |
| `relative_to_event` Thanksgiving + 1 | `4 THU NOV + 1D` |
| `relative_to_event` FOMC decision + 21 (announced dates) | `@FED_FOMC_DECISION + 21D`, with `event FED_FOMC_DECISION = …` |
| `nth_weekday_prev_business` | `3 FRI MAR > PRECEDING` |
| `last_business_day` February | `1 MAR - 1B` |
| `conditional` 24 Dec if Mon–Thu | `24 DEC IF MON TUE WED THU` |
| `hijri_fixed` Shawwal 1 | `1 SHAWWAL` (the Umm al-Qura date; moon sighting can move it by a day) |
| `hebrew` Tishrei 10, eve closed | `(10 TISHRI - 1D : 1D : 10 TISHRI)` |
| `chinese_lunisolar` month 1, day 1, offset −1 | `1 ZHENGYUE - 1D` |
| `span_days` 2, `duration_span` | ranges: `(2 DEC : 1D : 2 DEC + 1D)`, `(9 DHULHIJJAH : 1D : 12 DHULHIJJAH)` |

**Rolling**, seen from the base date: `TOM` is `T + 1B` and `SPOT [PAIR]` is `T > SPOT [PAIR]`. `> NEXT x` and `> PREV x` move to the
first `x` strictly after or before the date, where `x` is a weekday, `BD` (business day), `IMM` or an annual date, so
`T > NEXT 4 JUL` is this year's or next year's Independence Day. An ordinal counts: `T > NEXT 3RD FRI` is the third Friday,
`SPOT > NEXT 2ND IMM` the second IMM date, `T > PREV 2ND 4 JUL` the second 4 July before, while `T > NEXT 3 FRI JAN` stays the third
Friday of January. A set is the nearest of its anchors: `T > NEXT {MON, FRI}` is the next Monday or Friday, `T > NEXT {1 FEB, 1 MAY}`
the next of the two dates. `> BOM`, `> EOM`, `> BOQ`, `> EOQ`, `> BOH`, `> EOH`,
`> BOY` and `> EOY` move to the first or last day of the month, quarter, half year or year, and `H` is a half year (`6M`);
`> FOLLOWING`, `> PRECEDING` and `> MODFOLLOWING` (following unless that leaves the month) move to a business day of
the market. `T > NEXT 1 ZHENGYUE` is the next Chinese New Year. With these, the examples of another maturity rule language, which
nests selectors (`wednesday(3) of month(3) of quarter(2) of sp`), translate into this one; they are a section of the shared cases.

**Named events** bring dates that an authority announces, such as FOMC decisions or AGMs, into the language as data, the way a
market brings its holidays: the context maps a name to a rule, and `@FOMC` is every date of that event in the year of the base date,
each shifted by the rest of the expression (`@FOMC + 21D`, `@FOMC + 1B`, `@FOMC IF WED`). `T > NEXT @FOMC` and `T > PREV @FOMC`
move to the next or previous one. An event's rule is any schedule, so `event THANKSGIVING = 4 THU NOV` names a rule as well. A name
that isn't declared is an error, and so is an event with several dates at the end of a range. The implementations evaluate an item
once per date of its event: the event's date throws until the item binds it, which needs no change to how the twelve grammars build
expressions.

All implementations are tested against the same 427 cases in [examples/schedule.txt](examples/schedule.txt),
including invalid dates and syntax errors, 2186 cases in [examples/catalog.txt](examples/catalog.txt) and 3347 in
[examples/date-holidays.txt](examples/date-holidays.txt). Each module has a test that reads all three files. The second one is generated by [scripts/catalog_cases.py](scripts/catalog_cases.py) from the holiday rule catalog of the
holidays-events project: every rule the language can express (88 of 151) is translated into a schedule and evaluated for 2020 to 2030,
and the expected dates come from that project's own rule engine. The rules it can't express are listed at the end of the file; most of
them have no rule at all (dates announced by an authority). Rules anchored to such dates refer to a named event declared with the
dates holidays-events has observed, such as the FOMC decisions from the Fed's calendar. The same goes for the finance-business rules of that project's research
(`research/finance-dates/rules.yaml`, rules named `fd.*`): derivatives expiries, IMM dates, index review dates, period ends, filing
deadlines rolled to the next business day, DST changes and one-off regulatory dates, 123 rules in all; the research gaps that its rule
types can't state yet (published every business day, weekly, counted from a case-specific date, …) are listed with their reason. Cases that need a base date or a
market name them in a prefix, `[2015-06-09 XTEST] T + 2B => 2015-06-12`, and test markets are declared as data
(`market XTEST = 2015-06-10, …`: Saturdays, Sundays and these holidays). The right-hand side is itself a schedule, parsed by the
implementation under test and evaluated per year, so `market XNYS = 1 JAN {SUN + 1D}, 3 MON JAN, …` declares the NYSE calendar from
holiday rules (`ScheduleSemantics.withHolidayRules`).

The third file comes from [date-holidays](https://github.com/commenthol/date-holidays) (holiday data © its contributors,
[CC BY 3.0](https://creativecommons.org/licenses/by/3.0/)), whose own rule language for 207 countries and their regions is the
closest relative of this one: `easter -2`, `3rd monday in June`, `12-25 and if sunday then next monday`, `1 Shawwal`.
[scripts/date-holidays/generate.mjs](scripts/date-holidays/generate.mjs) translates each rule (`EASTER - 2D`, `3 MON JUN`,
`25 DEC, 25 DEC + 1D IF MON`, `1 SHAWWAL`), and the expected dates for 2024 to 2027 come from date-holidays itself. A case is only
written for a year in which date-holidays yields the rule, so `since 2005` or `in even years` need no syntax here. Equinoxes,
solstices and Qingming are compared with date-holidays (which uses the full VSOP87 of the astronomia package). Left out, with
counts at the end of the file: Orthodox Easter, times of day, the Persian and Bengali calendars, rules that
depend on other holidays, and the years in which a Hijri or Hebrew date of date-holidays differs from Umm al-Qura or the Hebrew
calendar the implementations use. A substitute of "observe both" (`25 DEC, 25 DEC + 1D IF MON`) is an item that only yields a date
on one weekday: the weekday of `D + k` is the target exactly when `D` falls on the weekday it replaces.

Every word belongs to exactly one word class: `EASTER` and month names such as `TISHRI` have six capital letters like a currency pair,
but they are keywords, so `T > SPOT EASTER` is an error everywhere. Names of events carry the sigil `@`, so they never compete with
either: `@EASTER` is a name.
The tools with a lexer get this from their rule order; Peggy, the Scala combinators and Lark's contextual lexer exclude the keywords in the pair pattern.

| Module | Grammar | Lines¹ | How the rule is built | How errors surface | Notable |
|---|---|---|---|---|---|
| antlr-java | [DateSchedule.g4](date-rule-antlr-java/src/main/antlr4/io/github/hjuergens/time/DateSchedule.g4) | 99 | actions with `returns […]` build functions | listener throws; `BailErrorStrategy` | `$start` is reserved, so range labels are `first`/`last`; an attribute `rule` becomes `rule_`; an optional token in a loop needs its own rule |
| combinators | [ScheduleParsers.scala](date-rule-combinators/src/main/scala/de/juergens/text/ScheduleParsers.scala) | 215 | `^^` functions of the context, `foldLeft` over the shifts | `NoSuccess` → exception | grammar is plain Scala; no generator |
| coco | [Schedule.atg](date-rule-coco/src/main/coco/Schedule.atg) | 132 | `(. … .)` actions build functions, `out` parameters | `errors.count`, messages on a stream | recovery keeps running actions, so they must tolerate garbage (now they only build functions); an attribute whose type contains `>` needs `<. … .>`, and the `out` attribute comes first |
| javacc | [Schedule.jj](date-rule-javacc/src/main/javacc/Schedule.jj) | 143 | productions return functions | `ParseException`, `TokenMgrException` | locals declared per production; LL(1) without lookahead hints |
| sablecc | [schedule.grammar](date-rule-sablecc/src/main/sablecc/schedule.grammar) + [ScheduleEvaluator](date-rule-sablecc/src/main/java/de/juergens/schedule/ScheduleEvaluator.java) | 88 + 131 | the typed AST is the rule; an evaluator walks it with the context | `ParserException`, `LexerException` | grammar without code; repeated elements need names |
| cup-jflex | [schedule.cup](date-rule-cup-jflex/src/main/cup/schedule.cup) + [schedule.jflex](date-rule-cup-jflex/src/main/jflex/schedule.jflex) | 75 + 59 | `{: … :}` actions build functions, left recursion | `Exception` from `parse()` | `%cupsym` must precede `%cup` |
| bison | [schedule.y](date-rule-bison/src/main/resources/schedule.y) | 198² | `{ … }` actions build functions, left recursion | `parse()` returns `false` | `$$` is untyped in Java; hand-written lexer |
| flex-bison | [parser.y](date-rule-flex-bison/src/parser.y) + [scanner.l](date-rule-flex-bison/src/scanner.l) | 160 + 94 | `{ … }` actions build a syntax tree; [semantics.c](date-rule-flex-bison/src/semantics.c) evaluates it | `yyparse()` returns non-zero; the scanner returns `YYerror` with its own message | reentrant (`api.pure`, `bison-bridge`); `$$` is typed through `%union`; token names are C enumerators, so they must not clash with the tree's |
| peg | [schedule.pegjs](date-rule-peg/schedule.pegjs) | 275³ | action blocks return functions, ordered choice | `SyntaxError` | `range()` is built into Peggy actions; date arithmetic hand-written |
| lark | [schedule.lark](date-rule-lark/src/lark_schedule/schedule.lark) + [Transformer](date-rule-lark/src/lark_schedule/__init__.py) | 47 + 89 | `Transformer` returns closures, one method per rule alias | `UnexpectedInput`; `VisitError` unwrapped | the same grammar runs as Earley and as LALR(1); the contextual lexer accepts `>SPOTUSDCAD` |
| antlr-python | [DateSchedule.g4](date-rule-antlr-python/DateSchedule.g4) + [visitor](date-rule-antlr-python/src/antlr_schedule/__init__.py) | 67 + 104 | visitor over the parse tree returns closures | raising error listener | Java actions can't be generated into Python; `range` becomes `range_()` |
| instaparse | [schedule.ebnf](date-rule-instaparse/src/main/clojure/io/github/hjuergens/instaparse/schedule.ebnf) + [transform](date-rule-instaparse/src/main/clojure/io/github/hjuergens/instaparse/schedule.clj) | 44 + 48 | `insta/transform`, one function per tag, onto `ScheduleSemantics` | `insta/failure?` → exception | scannerless with `:auto-whitespace`; GLL returns every parse, and each shared case has exactly one |

¹ Non-blank, non-comment lines. The JVM modules also share a 385-line `ScheduleSemantics` (market, context, rule builders, periods,
  business days, IMM and spot dates, ranges, annual dates, lunar months, rolling, named events); `java.time.Period` can't express business days, so `B`
  is a small `TemporalAmount` of its own. The Python modules share a 231-line `semantics.py` (copied, like the Java helper).
  Lunar months come from libraries: in Java `java.time`'s Hijrah calendar, ICU4J (Hebrew) and 6tail's lunar (Chinese), in Python
  hijridate, pyluach and lunardate, in JavaScript `Intl` and lunar-javascript. The C module computes the Hebrew calendar and takes
  Umm al-Qura and Chinese months from a table generated from hijridate and lunardate; its 594-line `semantics.c` ports the rest. ICU's astronomical Chinese calendar puts Chinese New Year
  2027 and 2030 a day off (the new moon falls within minutes of midnight in Beijing), which the generated cases caught.
² Including the hand-written scanner (about 75 lines).
³ Including JavaScript date arithmetic that reproduces `java.time` (about 175 lines with markets, business days, IMM and spot dates,
  annual dates, lunar months and rolling).

LALR tools (bison, flex-bison, CUP, SableCC) build the generators with conflict checking, and none of the four grammars has conflicts.
Coco/R's LL(1) check also passes without warnings.

## How it fits together

- [mise.toml](mise.toml) pins the toolchains (JDK, Maven, Node, Python, uv, Go) and defines one task per module.
  Every module builds with its native tool: Maven for Java and Scala, npm for Peggy, uv for Python.
- The parent [pom.xml](pom.xml) manages versions for the Java and Scala modules (Java 17 release, Scala 2.13, TestNG, JUnit Platform, SLF4J, plugins).
- Each JVM module generates its parser in `generate-sources` into `target/generated-sources/<tool>`, then compiles and tests it.
  Generated code is never checked in; the ANTLR Python parser is generated by the task `generate:antlr-python`.
- Tests run with surefire (`mvn test`). Integration tests (`*Spec`, antlr only) run with failsafe in `mvn verify`.
- Surefire 3.6 runs TestNG on the JUnit Platform and reads no TestNG suite files, so the antlr module selects its tests with `includes`.
- [.mvn/settings.xml](.mvn/settings.xml) (passed by [.mvn/maven.config](.mvn/maven.config)) redirects the repositories of the
  shut-down maven.java.net, which old parent POMs of plugin dependencies still declare, to Maven Central.

## Prerequisites

| Tool | Needed for |
|---|---|
| [mise](https://mise.jdx.dev/) | everything: `mise install` installs the pinned JDK 17, Maven, Node, Python, uv and Go |
| `bison` 3.x on `/usr/bin/bison` (`sudo apt install bison`) | optional; activates [date-rule-bison](date-rule-bison/README.md) |
| a C compiler, make, `flex` and `bison` (plus Go and `swig` for its Go binding), or Docker | [date-rule-flex-bison](date-rule-flex-bison/README.md); its Dockerfile has them all |

Without mise, install the tools from [mise.toml](mise.toml) yourself and run the commands of its tasks.

### Dev container

[.devcontainer/](.devcontainer) needs only Docker: the image adds `bison`, `flex`, a C compiler, `swig` and mise to a Debian base, and creating the
container runs `mise install`, so **every** module, both bison modules included, builds without installing anything on the host.
In VS Code, run "Dev Containers: Reopen in Container", then `mise run test` in its terminal.
Maven, mise and uv downloads are kept in Docker volumes (`date-parser-*`) and survive rebuilds of the container;
the Python virtual environments of the container are volumes as well, separate from the host's `.venv`.

## Build and test

```
mise install                           # toolchains, as pinned in mise.toml
mise run test                          # all modules; the module tasks run in parallel
mise tasks                             # list the tasks, e.g. test:lark, test:peg, docs
mvn -pl date-rule-antlr-java test           # a single JVM module, directly with Maven
mvn -pl date-rule-bison -Pbison -Dbison.executable=/opt/bison/bin/bison verify   # bison outside /usr/bin
docker build -t date-rule-flex-bison date-rule-flex-bison   # the C module without local flex and bison
docker run --rm -u "$(id -u):$(id -g)" -v "$PWD:/work" -w /work/date-rule-flex-bison date-rule-flex-bison make test
```

The first build downloads all dependencies; the JVM build then takes several minutes, mostly in the antlr and combinators modules.
Code coverage (JaCoCo) is written to `<module>/target/site/jacoco/`.

`examples/catalog.txt` is regenerated from a checkout of holidays-events next to this one, in that project's environment:

```
uv run --project ../holidays-events python scripts/catalog_cases.py ../holidays-events
```

`examples/date-holidays.txt` is regenerated with the pinned date-holidays version:

```
cd scripts/date-holidays && npm ci && npm run generate
```

## License

Apache License 2.0, see [LICENSE.txt](LICENSE.txt). The generated cases in
[examples/date-holidays.txt](examples/date-holidays.txt) contain holiday data of date-holidays under CC BY 3.0.
