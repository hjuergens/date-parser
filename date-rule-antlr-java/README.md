# date-rule-antlr-java

The [shared schedule example](../README.md#shared-example-schedule) with [ANTLR 4](https://www.antlr.org/) (4.13),
tested against [examples/schedule.txt](../examples/schedule.txt), [catalog.txt](../examples/catalog.txt) and
[date-holidays.txt](../examples/date-holidays.txt). Its `ScheduleSemantics` is the one the other JVM modules copy.

The older grammars of this module (Joda-Time periods, term structures, iCalendar, …) are parked in
[date-rule-experiments/antlr-java](../date-rule-experiments/antlr-java).

## The grammar

- [DateSchedule.g4](src/main/antlr4/io/github/hjuergens/time/DateSchedule.g4) computes the values in actions (`returns [LocalDate value]`).
- `DateSchedule.g4` has a second start rule, `ruleLine`, for one line of a rules file of [date-rule-runtime](../date-rule-runtime/README.md) (`rule @IMMH = …`, `assert @IMMH [2026-11-02 @XNYS] => …`); `DateSchedules.parseRuleLine` is its entry point, so that module reads its files without regular expressions.
- `> SPOT [PAIR]` has a rule of its own (`spot returns [String pair]`). A `PAIR?` label inside the `( … )*` loop of `expr`
  would be a field of the rule context, so a later `> SPOT` without pair would still see the pair of an earlier one.
- Periods, business days (`B`), IMM and spot dates come from the shared `ScheduleSemantics`; `B` is a `TemporalAmount`
  of its own, because `java.time.Period` can't express business days.
- The actions build functions (`ScheduleSemantics.plus($value, $period.value)`), not dates. The attribute of `schedule` is
  called `result`: ANTLR renames an attribute `rule` to `rule_`.
- [DateSchedules.parse](src/main/java/io/github/hjuergens/time/DateSchedules.java) installs a throwing error listener and `BailErrorStrategy`, so the first error ends the parse.
- Test: [DateSchedulesTest](src/test/java/io/github/hjuergens/time/DateSchedulesTest.java).
- The same rules without actions run on ANTLR's Python target in [date-rule-antlr-python](../date-rule-antlr-python/README.md).

```java
Rule rule = DateSchedules.parse("[T : 1Q : T + 11M]");    // a function of base date and market
rule.evaluate(new Context(LocalDate.of(2015, 1, 15), null));  // [2015-01-15, 2015-04-15, 2015-07-15, 2015-10-15]
```

Holiday rules and rolling (`4 JUL {SAT - 1D, SUN + 1D}`, `T > NEXT 3 MON JAN`, `> MODFOLLOWING`) are more alternatives in the
loop of `expr`; `annual` is one rule used by `date` and by the anchor of `> NEXT`. The keyword `'EASTER'` wins over the six-letter
`PAIR`, because ANTLR defines the implicit literal tokens before the lexer rules.
Lunar month names (`1 SHAWWAL`, `10 TISHRI`, `1 ZHENGYUE`) are more alternatives of `MONTH`, which comes before `PAIR`
for the same reason; `item` adds `'IF' (w+=WEEKDAY)+` to a list item (`24 DEC IF MON TUE WED THU`).
Ordinals and sets (`> NEXT 3RD FRI`, `> NEXT {MON, FRI}`): the lexer rule `ORDINAL : [0-9]+ ('ST' | 'ND' | 'RD' | 'TH')` wins over `NUM` by the longer match; the count is a rule of its own, `ordinal returns [int n = 1] : (ORDINAL)? ;`, like `spot`, so a label in the loop of `expr` doesn't keep the count of an earlier shift, and `anchor` is a `target` or `'{' target (',' target)* '}'`.

Named events: the lexer rule `EVENT : '@' [A-Z] [A-Z0-9_]*` is one more alternative of `date` and of `anchor`; the actions call `ScheduleSemantics.event` and `eventAnchor`, and `list` evaluates an item once per date of its event.

## Build and test

```
mvn -pl date-rule-antlr-java verify
```

The `antlr4-maven-plugin` generates the lexer and parser into `target/generated-sources/antlr4`;
[DateSchedulesTest](src/test/java/io/github/hjuergens/time/DateSchedulesTest.java) (TestNG) runs 6070 cases.
