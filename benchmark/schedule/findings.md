## Findings

Written by hand for the runs above; `report.py` appends this section unchanged. The Ryzen 3 3200G runs measured
aed23d6, a snapshot of the working tree that wasn't kept; its code is identical to the commit that added this report.
The i7-3770 runs measured e180b0f, which adds the Java target (closures and compiled) and has more cases, so its
checksum differs.

- **ANTLR's Python target is 90–105 times slower than the C library**: 240–360 µs against 2.7–3.4 µs per case to parse
  and evaluate. Most of it is parsing: ANTLR's Python runtime takes 210–275 µs per input, Flex and
  Bison 1.5–1.8 µs, so evaluating adds only about 15 % in Python. ANTLR's adaptive prediction (ALL(*)) runs as
  interpreted Python, while the Bison tables and the Flex automaton are compiled C.
- **Start-up**: Python needs 80–90 ms before its first result (the interpreter and the import of the generated parser),
  C and Go 2–5 ms.
- **The SWIG binding costs little when parsing** (1.3–1.4 × C) **and about double when evaluating** (1.9–2.1 × C on the
  i7-3770). Each evaluation from Go makes about ten cgo calls (context, error buffer, date list, the evaluation, one
  call per date). Go's spec reader also does more work than C's: a regular expression for the prefix, and a Go string
  copied to C for every input.
- **The garbage collector**: on the Ryzen 3 3200G (4 threads) Go's evaluate rounds are bimodal, about 36 or about 80 ms,
  so its median (66.5 ms, 4.2 × C) is not a stable number. A check on that machine with `GOGC=off` gave a constant 33 ms
  per round (2.1 × C, the same as on the i7-3770), against 39–78 ms with the default `GOGC=100` (20 collections in 16
  rounds). With 8 threads (i7-3770) the collector didn't show. Fewer allocations per call, such as reusing the context,
  error buffer and date list, would help a long-running Go service.
- **The machines**: the Ryzen 3 3200G is 10–15 % faster than the i7-3770 for C and Python. The i7-3770 runs under WSL2
  with 7 GB visible to Linux. The variation between runs is small apart from the Go case above: C is within about 10 %,
  Python within about 10 % (the first round of a run is the slowest).
- **ANTLR's Java target is about 7 times slower than C** and about 15 times faster than its Python target: 15 µs per
  case to parse, 25 µs to parse and evaluate (i7-3770). Its start-up is the slowest, about 175 ms for the JVM.
- **Lunar dates were 98 % of Java's evaluation**: before e180b0f, every `1 ZHENGYUE` or `15 AV` converted all 365 days of
  the year to the lunar calendar, on every evaluation, up to 100 ms per case. A trial run on a Cortex-A76 measured about
  1,030 µs per case to parse and evaluate. `ScheduleSemantics` now caches the search per day, month and year, and the
  same trial gave 30 µs.
- **Compiled rules evaluate barely faster than closures**: 3.65 against 3.78 µs per case when the rules are parsed once
  (evaluate only). The JIT already inlines the closures, and most of the time goes to `LocalDate` arithmetic and the
  calendars, which both call the same `ScheduleSemantics` helpers. Compiling costs about 130 µs per rule (ASM, class
  definition, a class loader per rule), so with 0.13 µs saved per evaluation it pays off only after about 1,000 evaluations of a rule. What it does bring:
  each rule is a class of its own that can be replaced and unloaded at run time.
