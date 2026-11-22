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
  before `"ADAR"`). Hijri dates come from the official Umm al-Qura table (`UMM_AL_QURA`, as in Java; `Intl`'s own table differs in 2030), Hebrew ones from `Intl`; Chinese ones from lunar-javascript, which a parser generated in memory
  can't import, so `parse(text, { Solar })` passes it in as an option. `Item` adds `24 DEC IF MON TUE WED THU`.
- Ordinals and sets (`> NEXT 3RD FRI`, `> NEXT {MON, FRI}`): `Counted` tries the ordinal `$([0-9]+ ("ST" / "ND" / "RD" / "TH"))` first, as `Number` in the annual date `3 FRI JAN` would take its digits; a set is `"{" Target ("," Target)* "}"`.
- Named events: `Event = "@" $([A-Za-z] [A-Za-z0-9_]* ("." [A-Za-z0-9_]+)*)`, an alternative of `Date` and `Anchor`; as there is no whitespace rule inside it, `@ FOMC` is an error.

## Rule files

[rulefile.pegjs](rulefile.pegjs) reads rule files (`rule KEY for CLASS in SCOPES ... = ...;`, `weekend`, `calendar`), the
same language as [date-rule-antlr-java's RuleFile.g4](../date-rule-antlr-java/README.md#rule-files), and its initializer
holds `RuleCatalog`, the JavaScript port of the Python `catalog.py` (calendars of scopes, set-level passes, ONE OF,
announcements as data).
- **One parser from two grammars:** [rule-files.mjs](rule-files.mjs) generates it with
  `peggy.generate([schedule.pegjs, rulefile.pegjs])`, so the rule grammar uses the helpers of the schedule grammar's
  initializer (`plus`, `roll`, `take`, `values`, ...) and some of its rules (`Number`, `Period`, `Month`). The playground
  of the site still generates its parser from `schedule.pegjs` alone.
- **Names:** the rules of `rulefile.pegjs` carry the lowercase names of `RuleFile.g4` (`item`, `shift`, `date`); Peggy's
  names are case-sensitive, so they don't clash with `Item`, `Shift`, `Date` of the schedule grammar. Helpers whose
  name the schedule grammar already uses get another one (`rollConvention`, `plusShift`).
- **Words:** `wordEnd` (`![A-Za-z0-9_]`) after every keyword, so `T` is not the start of `TOM`, `HOLIDAY` not the start
  of `HOLIDAYS`; `period` adds it to `Period`, so `1 DEC` is no period.
- **Context:** the catalog and the scope ride along in the context object, as `values()` copies it with `{ ...c }`; the
  events are a `Proxy` that answers `@key` from the catalog.
- **References:** a PEG backtracks, so the `@references` and the Hijri methods of a rule are read from `text()` of its
  declaration, without strings and comments.
- **Time zones:** `EQUINOX MAR "Asia/Tokyo"` takes the instant of the sun and the date there from `Intl.DateTimeFormat`.

```js
import { parseRules } from './rule-files.mjs';
parseRules('rule jp.equinox for national_holiday in country:JP = EQUINOX SEP "Asia/Tokyo";').holidays('country:JP', 2026);
// [{ date: { year: 2026, month: 9, day: 23 }, rule: 'jp.equinox', ... }]
```

## Build and test

```
mise run test:peg                # Node from mise.toml, then npm ci + npm test
npm ci && npm test               # or directly, with Node 20 or later
```

`npm run build` writes standalone ES module parsers to `target/schedule-parser.mjs` and `target/rulefile-parser.mjs`.
[test/schedule.test.mjs](test/schedule.test.mjs) (`node:test`) runs 6105 cases,
[test/rule-cases.test.mjs](test/rule-cases.test.mjs) the 59 of [examples/rules.txt](../examples/rules.txt) and
[test/rule-catalog.test.mjs](test/rule-catalog.test.mjs) 5 tests of attributes and announcements.
