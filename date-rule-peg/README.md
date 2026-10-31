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
mvn -pl date-rule-peg verify     # Maven downloads Node 24 into target/ and runs npm ci + npm test
npm ci && npm test               # or directly, with Node 20 or later
```

There are 5 tests (`node:test`) in [test/list.test.mjs](test/list.test.mjs).

## Status

Working.
