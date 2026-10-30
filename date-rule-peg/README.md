# date-rule-peg

A list grammar written as a parsing expression grammar (PEG) in JavaScript with [Peggy](https://peggyjs.org/),
the maintained successor of PEG.js.

## What it parses

[list.pegjs](list.pegjs) expands ranges into arrays of integers:

| Input | Result |
|---|---|
| `(3:7)` | `[3, 4, 5, 6, 7]` |
| `(3:5:14)` | `[3, 8, 13]` (start:step:end) |
| `( 2 : 7 : 23 )` | `[2, 9, 16, 23]` |
| `(9:1)` | `[]` |
| `(3:)` | `SyntaxError` |

This is the same `(start:step:end)` notation as `List.g4` in [date-rule-antlr](../date-rule-antlr/README.md).

## Usage

```js
import peggy from 'peggy';
const parser = peggy.generate(readFileSync('list.pegjs', 'utf8'));
parser.parse('(3:5:14)');   // [3, 8, 13]
```

`npm run build` writes a standalone ES module parser to `target/list-parser.mjs`.

## Build and test

```
mise run test:peg                # Node from mise.toml, then npm ci + npm test
npm ci && npm test               # or directly, with Node 20 or later
```

There are 193 tests (`node:test`) in [test/list.test.mjs](test/list.test.mjs) and [test/schedule.test.mjs](test/schedule.test.mjs).

## Shared example

Implements the [shared schedule example](../README.md#shared-example-schedule) and is tested against [examples/schedule.txt](../examples/schedule.txt).

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

## Status

Working.
