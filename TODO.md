# To do

- **TypeScript with [Chevrotain](https://chevrotain.io/)**: an LL(k) grammar written as code, with no generation step.
  It's a contrast to Peggy within the same language.
- **FX market**: a market for currency pairs with per-currency holiday calendars: business days counted per currency,
  USD only checked on the settlement day, the Latin American exceptions (T+1 must be a USD business day).
- **Holiday rules and rolling dates**: year-anchored dates (`4 JUL`, `3 MON JAN`, `LAST MON MAY`, `EASTER`),
  observance shifts, FOLLOWING/PRECEDING/MODFOLLOWING, `> NEXT`/`> PREV`, `BOM`/`EOM`, `TOM`, and markets declared
  from rules, so the holidays-events catalog can be expressed. Plan: [docs/plans/holiday-rules.md](docs/plans/holiday-rules.md).
- **Clojure with [instaparse](https://github.com/Engelberg/instaparse)**: a GLL parser on the JVM that accepts
  ambiguous and left-recursive grammars written as EBNF strings.
