# date-rule-experiments

Earlier experiments, moved out of the parser modules so that each of those holds only the
[shared schedule example](../README.md#shared-example-schedule). They are **parked**: not part of the Maven reactor
or of `mise run test`, kept as source for reference, and may not build any more.

One folder per module they came from, with the paths below it as they were there; each folder keeps that module's
README and build file as they were at the move.

| Folder | From | What it is |
|---|---|---|
| [antlr-java](antlr-java) | date-rule-antlr-java | Joda-Time periods, shifts and selectors (`+3M1W`, `>Q`), term structures, lists, iCalendar, ISO and ambiguous dates; `Ring`, ScalaCheck and specs2 examples |
| [combinators](combinators) | date-rule-combinators | Natural-language rules with Scala parser combinators: "next thursday", "2 London business days prior to the 3rd Wednesday of the delivery month", seasons |
| [coco](coco) | date-rule-coco | `Dates.atg`: date lists in ISO, German and US notation |
| [cup-jflex](cup-jflex) | date-rule-cup-jflex | A Pascal-subset lexer (JFlex) with a skeleton CUP grammar |
| [bison](bison) | date-rule-bison | An infix calculator with error recovery (`calc.y`); the old Windows binaries of win_flex_bison |
| [javacc](javacc) | date-rule-javacc | `Simple1.jj` (brace matching); JUnit 4 runner experiments (Theories, parameterized and dynamic suites) |
| [sablecc](sablecc) | date-rule-sablecc | Arithmetic expressions (`tiny`, `simpleAdder`) with a stack evaluator |
| [peg](peg) | date-rule-peg | `(start:step:end)` range lists (`list.pegjs`) with a browser demo |
| [antlr-clojure](antlr-clojure) | date-rule-antlr-clojure | A Leiningen project calling ANTLR from Clojure (JSON and selector grammars) |
