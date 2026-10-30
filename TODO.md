# To do

- **TypeScript with [Chevrotain](https://chevrotain.io/)**: an LL(k) grammar written as code, with no generation step.
  It's a contrast to Peggy within the same language.
- **FX market**: a market for currency pairs with per-currency holiday calendars: business days counted per currency,
  USD only checked on the settlement day, the Latin American exceptions (T+1 must be a USD business day).
- **Lunar calendars** for holiday rules: Hijri (`1 SHAWWAL`), Hebrew (`10 TISHRI`) and Chinese dates, through
  `java.time.chrono.HijrahDate`/ICU4J, `hijridate`/`convertdate` and `Intl.DateTimeFormat`. A Hijri date can occur twice in one
  Gregorian year; the rolling form `T > NEXT 1 SHAWWAL` is always unique. Moon sighting stays outside the language.
  See [docs/plans/holiday-rules.md](docs/plans/holiday-rules.md).
- **More holiday rule types**: conditions (`24 DEC IF MON..THU`), spans of several days, references to other named rules,
  the end-of-month rule for tenors from spot, and a business-day convention per element of a range.
- **Clojure with [instaparse](https://github.com/Engelberg/instaparse)**: a GLL parser on the JVM that accepts
  ambiguous and left-recursive grammars written as EBNF strings.
