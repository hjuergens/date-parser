# date-rule-runtime

Rules in the schedule language, changed while a Java application runs: a rules file that is validated, swapped atomically
and watched for changes. It builds on [date-rule-antlr-java](../date-rule-antlr-java/README.md): its parser and the
shared `ScheduleSemantics`.

## Why no HotSwap or JVMTI

A parsed schedule is already an object, `ScheduleSemantics.Rule`, a function of the context (base date, market, events).
Changing a rule at run time means parsing the new text and replacing the object. Redefining classes (HotSwap,
`Instrumentation.redefineClasses`, JVMTI) would only change method bodies of existing classes and needs an agent;
nothing here needs that. What is left is the infrastructure: where the rules come from, how they are checked before
they take effect, and how readers see a consistent version while a change happens.

## The rules file

Like the `market` and `event` lines of [examples/schedule.txt](../examples/schedule.txt), plus `rule` and `assert` lines:

```
market @XNYS = 1 JAN {SUN + 1D}, 3 MON JAN, ...        holidays (Saturdays and Sundays are always closed)
event @FOMC = 2026-01-28, 2026-03-18, ...              a named event, usable as @FOMC in rules
rule @IMMH = T > NEXT 3 WED MAR                        a named rule: any schedule of the language
assert @IMMH [2026-11-02 @XNYS] => 2027-03-17           a assertion: rule, [base] or [base market], expected dates
```

Each line is parsed by the grammar of the language itself: `DateSchedule.g4` of date-rule-antlr-java has a second
start rule, `ruleLine`, next to `schedule`, so no regular expression reads the file and a syntax error comes with its
column. Names carry the sigil `@`, as events already do: a bare `IMMH` or `XNYS` would clash in the lexer with keywords,
months and six-letter currency pairs. Expected dates are ISO dates separated by commas, `none` or `error`; lines starting
with `#` are comments. The file is still read line by line, so a reload reports every problem of the file, not only the
first. [example.rules](src/test/resources/example.rules) has the IMM, quarter-boundary and business-day rules
of the MaturityRule comparison with their assertions.

## API

```java
RuleRegistry registry = new RuleRegistry();
try (RuleFileWatcher watcher = new RuleFileWatcher(Path.of("app.rules"), registry)) {
    List<LocalDate> dates = registry.current().evaluate("IMMH", LocalDate.now(), "XNYS");
}
```

- `RuleBook` is one version of the file, parsed and immutable. `RuleBook.parse(text)` reports every problem with its
  line (syntax with the column, an invalid date, a name declared twice, a assertion of an unknown rule or market), and
  `verify()` runs the assertions. The API takes names without `@`: `evaluate("IMMH", base, "XNYS")`.
- `RuleRegistry.reload(text)` parses and verifies a new version and swaps it in only if both pass; otherwise the
  current version stays and the result lists the problems. `rollback()` returns to the previous version;
  `onChange` and `onRejected` take listeners.
- `RuleFileWatcher` loads the file at once and again whenever it is saved. It watches the directory, because many
  editors save by writing a new file and renaming it, and takes the events of one save together (200 ms).

**Thread safety:** a reader takes one snapshot with `registry.current()` and evaluates on it, without locks. Reloads are
serialised, and a reader never sees half of a change. A market caches the holidays of the years it has been asked
about; the cache in `ScheduleSemantics.withHolidayRules` is a `ConcurrentHashMap`.

## Demo

```
$ java -cp ... io.github.hjuergens.runtime.Main --watch app.rules SECOND_LAST_BD 2026-11-02 XNYS
version 1: SECOND_LAST_BD = [2026-11-27]
version 2: SECOND_LAST_BD = [2026-11-25]                  after changing the rule to T > 4TH LAST BD IN MONTH
rejected, version 2 stays:
  line 14: column 30 token recognition error at: 'X' ...        after saving a typo
```

## Tests

`mvn -pl date-rule-antlr-java,date-rule-runtime install` (`-am` builds the parser module first). The tests parse the
example file and run its assertions, report each kind of problem with its line, reject broken reloads, check that 8 reading
threads never see two versions mixed while 50 reloads alternate, and save a watched file in place and by rename.
