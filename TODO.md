# To do

- **TypeScript with [Chevrotain](https://chevrotain.io/)**: an LL(k) grammar written as code, with no generation step.
  It's a contrast to Peggy within the same language.
- **FX market**: a market for currency pairs with per-currency holiday calendars: business days counted per currency,
  USD only checked on the settlement day, the Latin American exceptions (T+1 must be a USD business day).
- **Lunar months with `> NEXT`**: `T > NEXT 1 SHAWWAL` looks in the year of the date and the next, so it fails in a year in which
  the Hijri date falls twice; it should take the first occurrence after the date. Moon sighting stays outside the language.
- **More holiday rule types** from the catalog (see the end of [examples/catalog.txt](examples/catalog.txt)): spans that start
  before a named event (the Fed's blackout from the second Saturday before a meeting), weekend regimes per market (Friday and Saturday in
  Saudi Arabia and the UAE), Orthodox Easter, the UAE's "Ramadan 30 if the month has 30 days", the end-of-month rule for tenors from
  spot, and a business-day convention per element of a range (`(20 JAN : 1M : 20 DEC) > FOLLOWING`).
- invest and make use of: https://natty-parser.github.io/
- Webui using peg
- https://en.wikipedia.org/wiki/Comparison_of_parser_generators
- C code generation and swig
- format of the calendar