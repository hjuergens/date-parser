# date-rule-cup-jflex

The [shared schedule example](../README.md#shared-example-schedule) with [JFlex](https://jflex.de/) (lexer) and [CUP](https://www2.cs.tum.edu/projects/cup/) (LALR(1) parser), the classic lex/yacc pair for Java,
tested against [examples/schedule.txt](../examples/schedule.txt), [catalog.txt](../examples/catalog.txt) and
[date-holidays.txt](../examples/date-holidays.txt).

The other experiments of this module (the Pascal lexer) are parked in
[date-rule-experiments/cup-jflex](../date-rule-experiments/cup-jflex).

## The grammar

- [schedule.cup](src/main/cup/schedule.cup) uses left recursion for lists and period chains, which is natural for LALR(1).
- CUP has no optional elements, so `> SPOT [PAIR]` is written as two alternatives.
- [schedule.jflex](src/main/jflex/schedule.jflex) is its lexer: longest match separates `IMM`, `SPOT`, currency pairs (`[A-Z]{6}`) and unit letters. `%cupsym ScheduleSym` must come before `%cup`, otherwise the EOF action refers to `sym`.
- CUP runs once per grammar (two plugin executions); JFlex processes every `.jflex` file.
- `Schedules.parse(text)` throws on the first syntax error ("Can't recover from previous error(s)").
- Test: [SchedulesTest](src/test/java/io/github/hjuergens/schedule/SchedulesTest.java).
- Holiday rules and rolling: the `'>'` token is now `GT`, since `NEXT` is a keyword. In the JFlex spec, `"EASTER"` comes before
  `[A-Z]{6}`: on a match of the same length the earlier rule wins.
- Lunar months and conditional dates: Lunar month names (`1 SHAWWAL`, `10 TISHRI`, `1 ZHENGYUE`) are more alternatives of `MONTH` in the JFlex spec, before `[A-Z]{6}` too;
  `item` and `weekdays` add `24 DEC IF MON TUE WED THU`, without conflicts.
- Ordinals and sets (`> NEXT 3RD FRI`, `> NEXT {MON, FRI}`): the JFlex rule `[0-9]+ ("ST" | "ND" | "RD" | "TH")` returns `ORDINAL` (the longest match beats `NUM`), and CUP has `counted ::= anchor | ORDINAL anchor`, `anchor ::= target | LBRACE targets RBRACE`, without conflicts.
- Selection (`> 3RD WED IN MONTH`): `count ::= ORDINAL | ORDINAL LAST | LAST` returns a negative count from the end; `select ::= chosen | LBRACE targets RBRACE` keeps `LAST MON MAY` in braces, so `LAST` can only be the count there. No conflicts.
- Named events: the JFlex rule `"@" [A-Z] [A-Z0-9_]*` returns `EVENT` with the name as value; in CUP it is one more alternative of `date` and `anchor`.

## Build and test

```
mvn -pl date-rule-cup-jflex verify
```

- `com.github.vbmacher:cup-maven-plugin` (CUP 11b) generates into `target/generated-sources/cup`.
- `de.jflex:jflex-maven-plugin` (JFlex 1.9) generates into `target/generated-sources/jflex`.
- [SchedulesTest](src/test/java/io/github/hjuergens/schedule/SchedulesTest.java) (TestNG) runs 6070 cases.
