# date-rule-javacc

The [shared schedule example](../README.md#shared-example-schedule) with `ph-javacc-maven-plugin`, which uses [ParserGeneratorCC](https://github.com/phax/ParserGeneratorCC), a maintained fork of [JavaCC](https://javacc.github.io/javacc/); it generates an LL(k) recursive-descent parser,
tested against [examples/schedule.txt](../examples/schedule.txt), [catalog.txt](../examples/catalog.txt) and
[date-holidays.txt](../examples/date-holidays.txt).

The other experiments of this module (the brace-matching grammar `Simple1` and the JUnit 4 runner experiments) are parked in
[date-rule-experiments/javacc](../date-rule-experiments/javacc).

## The grammar

[Schedule.jj](src/main/javacc/Schedule.jj) is LL(1): each production declares its locals and returns its value.
After `">"` the production branches on `"IMM"` or `"SPOT"` followed by an optional `<PAIR>`; `Period()` returns a
`TemporalAmount`, because business days (`B`) aren't a `java.time.Period`.
`Schedule.parse(text)` returns a `ScheduleSemantics.Rule` (evaluate it with base date and market), or throws `ParseException` (syntax),
`TokenMgrException` (unknown character), or `DateTimeException`.
Test: [ScheduleTest](src/test/java/io/github/hjuergens/parser/ScheduleTest.java).

Holiday rules and rolling: `Date()` reads the number that starts both `2015-06-09` and `3 MON JAN`, then `AnnualTail(n)` continues.
The tokens `MONTH`, `WEEKDAY`, `ROLL` and `EASTER` are declared before `PAIR`: on a match of the same length the earlier one wins.
Lunar month names (`1 SHAWWAL`, `10 TISHRI`, `1 ZHENGYUE`) are more alternatives of `MONTH`; `Item()` adds
`( "IF" ( <WEEKDAY> )+ )?` to a list item. `ScheduleTest` runs 6070 cases (with the case number in each name: two cases
differ only in their base date).
Ordinals and sets (`> NEXT 3RD FRI`, `> NEXT {MON, FRI}`): `< ORDINAL : (["0"-"9"])+ ("ST" | "ND" | "RD" | "TH") >` is longer than `NUM` on the same input, so the token manager decides and `Counted()` needs no lookahead; a set is `"{" Target() ( "," Target() )* "}"`.

Named events: the token `< EVENT : "@" ["A"-"Z"] (["A"-"Z", "0"-"9", "_"])* >` is one more choice in `Date()` and `Anchor()`; no lookahead is needed, as no other token starts with `@`.

## Build and test

```
mvn -pl date-rule-javacc verify
```

Sources are generated into `target/generated-sources/javacc1`.
[ScheduleTest](src/test/java/io/github/hjuergens/parser/ScheduleTest.java) (JUnit 4) runs 6070 cases.
