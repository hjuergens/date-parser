## Findings

Written by hand for the runs above; `report.py` appends this section unchanged. The runs measured aed23d6, a snapshot
of the working tree that wasn't kept. Its code is identical to the commit that adds this report, which differs from it only in
documentation, `report.py` and the results.

- **ANTLR's Python target is about 90 times slower than the C library**: about 240 µs against 2.7 µs per case to parse
  and evaluate, on both machines. Most of it is parsing: ANTLR's Python runtime takes 210–240 µs per input, Flex and
  Bison 1.5–1.8 µs, so evaluating adds only about 15 % in Python. ANTLR's adaptive prediction (ALL(*)) runs as
  interpreted Python, while the Bison tables and the Flex automaton are compiled C.
- **Start-up**: Python needs 80–90 ms before its first result (the interpreter and the import of the generated parser),
  C and Go 2–5 ms.
- **The SWIG binding costs little when parsing** (1.3–1.4 × C) **and about double when evaluating** (2.1 × C on the
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
