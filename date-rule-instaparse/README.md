# date-rule-instaparse

The [shared schedule example](../README.md#shared-example-schedule) parsed with
[instaparse](https://github.com/Engelberg/instaparse), a Clojure library that builds a GLL parser from a grammar written as
EBNF. GLL accepts any context-free grammar, also ambiguous and left-recursive ones, and can return every parse of an input.

## How it works

- [schedule.ebnf](src/main/clojure/io/github/hjuergens/instaparse/schedule.ebnf) is the grammar. instaparse reads it at run
  time; there is no generator step. `<…>` hides a rule or a literal from the tree, so `'+'`, `'>'` and the keywords never reach
  the transform.
- [schedule.clj](src/main/clojure/io/github/hjuergens/instaparse/schedule.clj) turns the tree into the rule with
  `insta/transform`, one function per tag. The functions call `ScheduleSemantics` (a copy of the Java helper all JVM modules
  share), so the semantics are the same as in the other JVM modules; a shift becomes a function from expression to expression,
  and `expr` reduces over them.
- [Schedules.java](src/main/java/io/github/hjuergens/instaparse/Schedules.java) is the Java entry point: it requires the
  namespace through `clojure.java.api.Clojure` and calls `parse`. The Clojure files are resources (no AOT compilation, no Clojure
  Maven plugin), so the module builds with the plain `maven-compiler-plugin`.

```clojure
(require '[io.github.hjuergens.instaparse.schedule :as schedule])
(.evaluate (schedule/parse "T > SPOT USDCAD")
           (io.github.hjuergens.instaparse.ScheduleSemantics$Context. (java.time.LocalDate/of 2015 6 12) nil))
;; [2015-06-15]
```

## Notes

- instaparse is scannerless. `:auto-whitespace :standard` allows whitespace between any two tokens, so `2015-06-12 >SPOTUSDCAD`
  parses, as with Peggy and the Scala combinators. A named event (`@FOMC`) is one regex, so `@ FOMC` stays an error.
- Without a lexer nothing reserves the six-letter keywords, so `PAIR` starts with a negative lookahead,
  `!('EASTER' | 'SHABAN' | …)`, otherwise `T > SPOT EASTER` would read a pair.
- Month names are one regex, in which `ADARII` must come before `ADAR`: a regex alternation takes the first match.
- `insta/parses` returns all parses of an input. Every input of the shared files has exactly one (or none, for the syntax
  errors), so the grammar is unambiguous on them.
- instaparse is published on Clojars, so the POM adds that repository.

## Tests

[SchedulesTest](src/test/java/io/github/hjuergens/instaparse/SchedulesTest.java) runs the 5716 cases of
[examples/schedule.txt](../examples/schedule.txt), [examples/catalog.txt](../examples/catalog.txt) and
[examples/date-holidays.txt](../examples/date-holidays.txt) with TestNG, the same harness as in the other JVM modules
(5716 TestNG tests).

```
mvn -pl date-rule-instaparse test
```

A Leiningen experiment from 2018 lived in this folder before (instaparse tutorial grammars and third-Wednesday adjusters);
it is in the git history, for example at commit `3e416a7`.
