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

## Existing formats

No open standard holds both the holidays and the conventions:

- Holidays: iCalendar (RFC 5545, with RFC 7529 `RSCALE` for Hijri, Hebrew and Chinese recurrences) as published by
  exchanges and central banks; commercial data (Copp Clark, Bloomberg, Refinitiv); open public-holiday rules
  (date-holidays, Nager.Date, OpenHolidays). ISO 10383 MIC codes (`XNYS`) name markets but carry no holidays.
- Conventions: FpML describes how calendars are applied, not their holidays: `businessCenters` names them,
  `BusinessDayAdjustments` combines a roll convention with one or more business centres, and `RelativeDateOffset`
  counts business or calendar days from another date in given business centres. EMTA templates describe NDF fixings
  in prose. Spot lags live in code or library configuration (OpenGamma Strata, QuantLib).

This plan follows FpML for the conventions; the holidays stay rules of the language (`market … = <schedule>`).

### Mapping to FpML

| FpML | Schedule language |
|---|---|
| `businessCenter` `USNY`, `EUTA`, `JPTO`, … | a market name: `market USNY = …` |
| `BusinessDayAdjustments` (`MODFOLLOWING`, centres `EUTA`, `JPTO`) | `> MODFOLLOWING EUTA+JPTO` |
| `RelativeDateOffset` (−2, `D`, `Business`, centre `KRSE`, relative to the value date) | `<value date> - 2B KRSE` |
| `RelativeDateOffset` with `dayType` `Calendar` and an adjustment | `- 2D > PRECEDING KRSE` |
| `businessDayConvention` `NONE` | no roll |
| `FOLLOWING`, `PRECEDING`, `MODFOLLOWING` | the same keywords |

FpML's `MODPRECEDING`, `NEAREST`, `FRN` and `EOM` conventions have no counterpart yet (`> EOM` here only moves to the
month end and doesn't roll).

### Business centre codes

The four-letter FpML codes (country and city) become the market names of the FX cases, with a fixed map from currency
to its main centre, used by `SPOT PAIR`:

| Currency | Centre | | Currency | Centre |
|---|---|---|---|---|
| USD | `USNY` New York | | KRW | `KRSE` Seoul |
| EUR | `EUTA` TARGET | | INR | `INMU` Mumbai |
| GBP | `GBLO` London | | TWD | `TWTA` Taipei |
| JPY | `JPTO` Tokyo | | BRL | `BRSP` São Paulo |
| CHF | `CHZU` Zurich | | PHP | `PHMA` Manila |
| CAD | `CATO` Toronto | | MXN | `MXMC` Mexico City |
| SEK | `SEST` Stockholm | | NOK | `NOOS` Oslo |
| DKK | `DKCO` Copenhagen | | AUD | `AUSY` Sydney |

Codes to be checked against the FpML business-center coding scheme before use. MIC codes (`XNYS`, `XFED` here) stay
for exchange and other calendars; a market name of four letters starting with X is a MIC-like name, the others are
business centres.

## Proposal

1. FX calendars in the test files are declared under their business centre code: `market EUTA = …`,
   `market USNY = …`, `market SEST = …`. The test prefix `[base market]` stays for the single-market cases.
2. `SPOT PAIR` takes the calendars of the pair's centres from the map above when they are declared, with the rules
   above (USNY for the spot date of a cross); without them it behaves as now.
3. Business days and rolls in a named or a joint calendar, as in FpML: `+ 2B SEST`, `- 2B KRSE`,
   `> MODFOLLOWING EUTA+JPTO`. Open question: the joint calendar of a pair implicitly after `SPOT PAIR` in the same
   expression, or always written.
4. `>= IMM` (and `>= NEXT x`?): the next IMM date on or after the date, instead of `- 1D > IMM`.
5. Optional, later: export a market to iCalendar (one `VEVENT` per holiday of a year range) and import an `.ics` that
   lists dates, for comparison with published exchange calendars.

## Cases (to write before implementing)

- EURJPY spot over a Japanese holiday, over a TARGET holiday, and with a USD holiday as the spot date and in between.
- USDMXN with a USD holiday on T + 1.
- EURSEK spot over Midsummer Eve; EURSEK + 1M > MODFOLLOWING at a month end with a SEK holiday.
- A USDKRW NDF: value date (`> MODFOLLOWING USNY+KRSE`) and fixing date (`- 2B KRSE`).
- `>= IMM` on an IMM date and the day before.

## Implementation

As for named events: `ScheduleSemantics.java` (7 copies), `ScheduleParsers.scala`, `schedule.pegjs`, `semantics.py`
(2 copies); the grammars get the calendar suffix and `>=`; the harnesses read currency markets.
