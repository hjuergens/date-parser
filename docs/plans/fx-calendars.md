# Plan: FX spot with the calendars of the pair, and "on or after" for IMM

Status: proposal, not implemented. The cases for the reference date of `> IMM` (today, tomorrow, spot, spot + 1B,
on or after spot) are in `examples/schedule.txt`, section "next IMM date from today, tomorrow or spot"; they work with
the language as it is.

Not part of this plan: the meaning of the base date for annual dates and events ("in the year of T"); it needs
rethinking first.

## Context

A schedule is evaluated with one market, and `B`, `SPOT`, `FOLLOWING` etc. use its business days. FX dates need the
calendars of both currencies of a pair, and for a cross also the USD calendar:

- `SPOT EURJPY`: count two days that are business days in EUR and in JPY; the spot date must also not be a USD holiday
  (then roll forward), but a USD holiday on the day in between counts.
- `SPOT USDxxx`: count two days in xxx; a USD holiday on T + 1 counts as a business day, except for some currencies
  (e.g. MXN; the list is to be checked), where it doesn't; the spot date must be a business day in both.
- T + 1 pairs (USDCAD, USDTRY, USDPHP, USDRUB) as today.
- Roll conventions after a tenor (`SPOT EURJPY + 1M > MODFOLLOWING`) need the joint calendar of the pair.
- An NDF fixing is N business days of the fixing centre before the value date (KRW, INR, TWD, BRL: 2; PHP: 1).

## Proposal

1. Markets per currency in the test files: `market EUR = …`, `market USD = …`, `market SEK = …` (a market name of
   three letters is a currency calendar). The test prefix `[base market]` stays for the single-market cases.
2. `SPOT PAIR` takes its calendars from the pair when both currencies have a market, with the rules above; without
   currency markets it behaves as now.
3. Business days and rolls of a named calendar or of a joint one: `+ 2B SEK`, `- 2B KRW`, `> MODFOLLOWING EUR+JPY`.
   Open question: the joint calendar of a pair implicitly after `SPOT PAIR` in the same expression, or always written.
4. `>= IMM` (and `>= NEXT x`?): the next IMM date on or after the date, instead of `- 1D > IMM`.

## Cases (to write before implementing)

- EURJPY spot over a Japanese holiday, over a TARGET holiday, and with a USD holiday as the spot date and in between.
- USDMXN with a USD holiday on T + 1.
- EURSEK spot over Midsummer Eve; EURSEK + 1M > MODFOLLOWING at a month end with a SEK holiday.
- A USDKRW NDF: value date and fixing date (`- 2B KRW`).
- `>= IMM` on an IMM date and the day before.

## Implementation

As for named events: `ScheduleSemantics.java` (7 copies), `ScheduleParsers.scala`, `schedule.pegjs`, `semantics.py`
(2 copies); the grammars get the calendar suffix and `>=`; the harnesses read currency markets.
