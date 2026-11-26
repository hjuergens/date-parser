# date-rule-bison

The [shared schedule example](../README.md#shared-example-schedule) with [GNU Bison](https://www.gnu.org/software/bison/)'s Java skeleton (LALR(1)),
tested against [examples/schedule.txt](../examples/schedule.txt), [catalog.txt](../examples/catalog.txt) and
[date-holidays.txt](../examples/date-holidays.txt).

The other experiments of this module (the infix calculator and the bundled Windows binaries of win_flex_bison) are parked in
[date-rule-experiments/bison](../date-rule-experiments/bison).

## The grammar

- [schedule.y](src/main/resources/schedule.y) generates `ScheduleParser`.
- It has no `error` rules, so `parse()` returns `false` on the first syntax error.
- The scanner is hand-written in the epilogue. It reads words of capital letters as a whole and classifies them:
  `IMM`, `SPOT`, a six-letter currency pair, or a unit letter (`D W M Q Y B`). Anything else returns `YYUNDEF`.
- `> SPOT [PAIR]` is two rules (`expr '>' SPOT` and `expr '>' SPOT PAIR`); the LALR(1) table decides on the next token without conflict.
- In Bison's Java skeleton `$$` is a plain `Object`, while `$1`…`$n` are typed, so the list is built in a local variable.
- The build runs bison with `-Werror=conflicts-sr -Werror=conflicts-rr`.
- Test: [ScheduleParserTest](src/bison-test/java/io/github/hjuergens/bison/ScheduleParserTest.java).

```java
ScheduleLexer lexer = new ScheduleLexer("2015-06-09, 01.01.2000 + 1Y");
ScheduleParser parser = new ScheduleParser(lexer);
if (parser.parse()) parser.rule.evaluate(new ScheduleSemantics.Context(null, null));   // [2015-06-09, 2001-01-01]
```

Holiday rules and rolling add `annual`, `anchor` and the observance `shifts` (left recursive, like `exprs`); the scanner tests the
keywords (`EASTER` among them) before it takes a six-letter word as a currency pair.
Lunar month names (`1 SHAWWAL`, `10 TISHRI`, `1 ZHENGYUE`) are more alternatives of `MONTH` in the scanner's
keyword test; `item` and `weekdays` add `24 DEC IF MON TUE WED THU`, left recursive again.
Ordinals and sets (`> NEXT 3RD FRI`, `> NEXT {MON, FRI}`): the scanner returns `ORDINAL` when `ST`, `ND`, `RD` or `TH` follow the digits; `counted: anchor | ORDINAL anchor` and `anchor: target | '{' targets '}'` add no conflicts.
Selection (`> 3RD WED IN MONTH`): `count: ORDINAL | ORDINAL LAST | LAST` with a negative count from the end, and `select: chosen | '{' targets '}'`; the scanner returns `IN` and `PERIOD`. No conflicts.

Named events: the hand-written scanner returns `EVENT` for `@` followed by a letter, then letters, digits and `_`, in parts joined by single dots (the value is the name without the sigil); `EVENT` is one more alternative of `date` and `anchor`, without conflicts.

## Rule files

[rulefile.y](src/main/resources/rulefile.y) reads rule files (`rule KEY for CLASS in SCOPES ... = ...;`, `weekend`,
`calendar`), the same language as [date-rule-antlr-java's RuleFile.g4](../date-rule-antlr-java/README.md#rule-files),
and builds the declarations for [RuleCatalog](src/main/java/io/github/hjuergens/bison/RuleCatalog.java), the evaluator
shared by the JVM modules.
- **The parser class is the entry point:** the generated sources exist only in the `bison` profile, so no class in
  `src/main/java` may use them. `%define api.parser.class {RuleFiles}` names the parser after the entry point, and its
  `%code` holds `parseRules` and `schedule`, in the subpackage `rules`.
- **One start symbol:** `rule_file: declarations | exprs` reads a rule file or one schedule; the first token tells them
  apart.
- **LALR(1) without conflicts:** `-Werror=conflicts-sr` and `-Werror=conflicts-rr` hold here too.
- **Lambdas:** an action copies `$2` and `$3` into local variables before a lambda uses them, as Bison's `$n` reads the
  parser stack, which has moved on when the lambda runs.
- **Lexer:** hand-written in the epilogue like the one of `schedule.y`, with maps for the keywords; a lowercase word
  is a keyword, a word of a kind, precision or label, a scope (with `:`) or a key.

```java
RuleCatalog catalog = RuleFiles.parseRules("rule jp.equinox for national_holiday in country:JP = EQUINOX SEP \"Asia/Tokyo\";");
catalog.holidays("country:JP", 2026);   // [Occurrence[date=2026-09-23, rule=jp.equinox, ...]]
```

## Build and test

You need the `bison` executable; **without it, the module builds empty and has no tests**.

```
sudo apt install bison                     # profile 'bison' activates when /usr/bin/bison exists
mvn -pl date-rule-bison verify

mvn -pl date-rule-bison -Pbison -Dbison.executable=/path/to/bison verify   # bison installed elsewhere
```

With the profile active, `maven-antrun-plugin` runs bison in `generate-sources` and writes
`target/generated-sources/bison/io/github/hjuergens/bison/ScheduleParser.java`. The tests in
[src/bison-test/java](src/bison-test/java) are only compiled then (TestNG: 6105 schedule cases, 117 rule cases of
[examples/rules.txt](../examples/rules.txt) and 5 tests of attributes and announcements).
