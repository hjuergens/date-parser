# date-rule-compiled

The [shared schedule example](../README.md#shared-example-schedule) compiled to JVM bytecode: a schedule becomes a class
of its own that implements `ScheduleSemantics.Rule`, instead of the chain of closures that
[date-rule-antlr-java](../date-rule-antlr-java/README.md) builds. Tested against
[examples/schedule.txt](../examples/schedule.txt), [catalog.txt](../examples/catalog.txt) and
[date-holidays.txt](../examples/date-holidays.txt).

```java
Rule rule = CompiledRules.parse("SPOT + 1Q > EOQ > 3RD WED IN MONTH");   // a class of its own
rule.evaluate(new Context(LocalDate.of(2026, 11, 2), null));               // [2027-03-17]
```

## How it works

- **Parse:** [CompiledRules.parse](src/main/java/io/github/hjuergens/compiled/CompiledRules.java) parses with the grammar
  of date-rule-antlr-java. Its actions have already built every value the evaluation needs (periods, anchors, annual
  dates).
- **Generate:** [RuleCompiler](src/main/java/io/github/hjuergens/compiled/RuleCompiler.java) walks the parse tree and
  writes the bytecode of one class with [ASM](https://asm.ow2.io/) 9. The values of the actions become final fields,
  passed to the constructor.
  - An item with one date (a date and its moves, optionally with `IF`) becomes straight-line code on `LocalDate`. It
    calls the date-level helpers of `ScheduleSemantics` (`plus`, `roll`, `select`, `observe`, `Anchor.from`, …), so
    the semantics aren't written twice. A period of days, weeks, months or years is a direct `LocalDate.plus`, and
    `IF MON TUE` is a bit mask.
  - An item with several values (a series, an event, `&` or `\`, a pick, dates in year) binds them one after the
    other. It stays the closure rule the actions built, held in a field the code calls.
- **Load:** each rule has a [RuleClassLoader](src/main/java/io/github/hjuergens/compiled/RuleClassLoader.java) of its
  own. All rules share one class name, and a rule that is no longer referenced is unloaded with its loader. Replacing a
  rule at run time means parsing the new text; no HotSwap, JVMTI or agent is involved.

## Build and test

```
mvn -pl date-rule-compiled -am verify
```

[CompiledRulesTest](src/test/java/io/github/hjuergens/compiled/CompiledRulesTest.java) (TestNG) runs the 6070 cases
with every rule, market and event compiled, and checks that each rule has a class and loader of its own.
The [benchmark](../benchmark/schedule/README.md) compares compiled rules with the closures.
