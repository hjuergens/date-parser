# date-rule-peg

The [shared schedule example](../README.md#shared-example-schedule) with [Peggy](https://peggyjs.org/), the maintained successor of PEG.js: a parsing expression grammar (PEG) in JavaScript,
tested against [examples/schedule.txt](../examples/schedule.txt), [catalog.txt](../examples/catalog.txt) and
[date-holidays.txt](../examples/date-holidays.txt).

The other experiments of this module (the range-list grammar `list.pegjs` and its browser demo) are parked in
[date-rule-experiments/peg](../date-rule-experiments/peg).

## The grammar

- [schedule.pegjs](schedule.pegjs) uses ordered choice (`/`) for the three date notations.
- `parse()` returns the rule: a function of `{ base, market }`; a market is any object with `isBusinessDay(date)`.
- PEG has no separate lexer, so whitespace (`_`) is explicit in the rules.
- There is no `LocalDate` in JavaScript, so the grammar's initializer reproduces `java.time`'s month arithmetic
  (day clamped to the end of the month) in about 35 lines, plus about 25 lines for markets,
  business days, IMM and spot dates.
- A currency pair is `$[A-Z]|6|`, Peggy's repetition count. Since there is no lexer, `>SPOTUSDCAD` without a space
  also parses (as with the Scala combinators); the tools with a longest-match lexer read `SPOTUS` as a pair and reject it.
- Don't name a helper `range`: Peggy provides `range()` (the location of the match) in actions.
- Holiday rules and rolling: `"TOM"` must come before `"T"` in `Date`, and the pair is `!"EASTER" $[A-Z]|6|`; without a lexer
  nothing else would reserve the keyword. About 80 more lines of JavaScript compute annual dates, Easter and the roll conventions.
- Lunar months and conditional dates: Lunar month names (`1 SHAWWAL`, `10 TISHRI`, `1 ZHENGYUE`) are more alternatives of `Month` (ordered choice: `"ADARII"`
  before `"ADAR"`). Hijri and Hebrew dates come from `Intl`; Chinese ones from lunar-javascript, which a parser generated in memory
  can't import, so `parse(text, { Solar })` passes it in as an option. `Item` adds `24 DEC IF MON TUE WED THU`.
- Ordinals and sets (`> NEXT 3RD FRI`, `> NEXT {MON, FRI}`): `Counted` tries the ordinal `$([0-9]+ ("ST" / "ND" / "RD" / "TH"))` first, as `Number` in the annual date `3 FRI JAN` would take its digits; a set is `"{" Target ("," Target)* "}"`.
- Named events: `Event = "@" $([A-Z] [A-Z0-9_]*)`, an alternative of `Date` and `Anchor`; as there is no whitespace rule inside it, `@ FOMC` is an error.

## Build and test

```
mise run test:peg                # Node from mise.toml, then npm ci + npm test
npm ci && npm test               # or directly, with Node 20 or later
```

`npm run build` writes a standalone ES module parser to `target/schedule-parser.mjs`.
[test/schedule.test.mjs](test/schedule.test.mjs) (`node:test`) runs 6070 cases.
