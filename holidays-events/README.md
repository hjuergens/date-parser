# Projection rules of holidays-events

This directory holds the date rules of **holidays-events**, written in the rule language of this repository
(date-parser). You need no knowledge of either project to read them; this page explains what they are, how to read
them, how to run them and how far they can be trusted.

## 1. What these files are

holidays-events is a system that collects **market-relevant dates**: public holidays, days on which stock exchanges
and payment systems are closed or close early, central-bank rate decisions and minutes, and the release days of
statistics such as consumer prices, GDP or the US jobs report. Its main input is what the responsible authority
publishes (an exchange's holiday calendar, a central bank's meeting schedule, a statistics office's release
calendar).

Authorities publish only a year or two ahead. For the years beyond, holidays-events **projects** dates from rules:
"the third Monday in January", "Easter Sunday minus two days", "the fourth Thursday of the meeting month". These
files are those rules. Two principles apply throughout:

- **Rules only project; an announcement always wins.** The authority's published dates are data, never part of a
  rule. Once it publishes its dates for a year, they replace the projection for that year completely, even if the
  rule would have given other days.
- **A projection is only as good as its pattern.** Fixed holidays are certain. Meeting and release dates are
  projected only where the authority states the pattern itself, or where an observed pattern hit the authority's
  own dates exactly at least 80% of the time over at least two years. Where no pattern is good enough, there is no rule:
  the dates exist only once the authority publishes them, and a comment in the file says so.

Each rule was translated from the holidays-events rule catalog (one YAML file per region, named in each file's
header) and checked day by day against holidays-events' own rule engine (section 6).

## 2. Reading guide

Every file starts with a header listing its scopes (what each code stands for) and the authorities it relies on.
Every rule has a plain-English comment above it. Lines starting with `#` are comments.

### A rule declaration

```
rule xnys.juneteenth for juneteenth in exchange:XNYS valid from 2022-01-01 = 19 JUN {SAT - 1D, SUN + 1D}
  with kind = closed
  cites "NYSE holidays & trading hours" url "https://www.nyse.com/markets/hours-calendars" label official;
```

| Part | Meaning |
|---|---|
| `rule xnys.juneteenth` | the rule's unique key (lower case, dotted); other rules refer to it as `@xnys.juneteenth` |
| `for juneteenth` | the event (what the date is), shared by rules of different scopes |
| `in exchange:XNYS` | the scope: whose calendar this date belongs to |
| `valid from 2022-01-01` | optional; first and/or last day (`valid to`, inclusive) on which the rule applies |
| `= 19 JUN {SAT - 1D, SUN + 1D}` | the date expression, evaluated once per year |
| `with kind = closed, ...` | attributes: kind, session, precision, note |
| `cites "..." url "..." label official` | where the rule comes from |
| `;` | ends the declaration |

A rule may have several versions with the same key whose validity periods do not overlap, for example the UAE's
Eid al-Fitr before and after the 2025 change (`gcc.rules`) or China's Spring Festival (`apac_other.rules`).

### Scopes

A scope is written `type:CODE`. The types used here:

| Type | What it is | Examples |
|---|---|---|
| `country` | a country's public holidays | `country:GB`, `country:SA`, `country:CN` |
| `exchange` | a stock or derivatives exchange, coded by its market identifier code (ISO 10383 MIC) | `exchange:XNYS` (New York Stock Exchange), `exchange:XETR` (Xetra), `exchange:XSAU` (Saudi Exchange) |
| `central_bank` | a central bank: its closing days and its decisions | `central_bank:FED`, `central_bank:BOE`, `central_bank:BCB` |
| `settlement_system` | a payment system (real-time gross settlement) and its closing days | `settlement_system:TARGET2` (euro), `settlement_system:LYNX` (Canada), `settlement_system:SIC` (Switzerland) |
| `segment` | a market segment with its own calendar | `segment:US_BOND` (US dollar bond market, following SIFMA's recommendations) |
| `statistics_office` | an office that publishes statistics | `statistics_office:BLS`, `statistics_office:ONS`, `statistics_office:EUROSTAT` |
| `issuer` | a listed company, coded by its Legal Entity Identifier (LEI) | `issuer:529900K9B0N5BT694847` |

The scope also decides which days are business days for shifts such as `> PRECEDING` (see "Shifts and rolls"):
weekends from `weekends.rules`, plus every day that a rule of that scope marks `closed`.

### Dates

The date expression is evaluated for each year.

| Expression | Meaning | Used in |
|---|---|---|
| `25 DEC` | a fixed day | `gb.christmas_day` |
| `EASTER - 2D`, `EASTER + 39D` | Western Easter Sunday plus or minus days (Good Friday, Ascension) | `xswx.good_friday`, `xswx.ascension_day` |
| `EASTER - 48D` | Carnival Monday | `bcb.carnival_monday` |
| `3 MON JAN` | the third Monday in January | `xnys.mlk_day` |
| `LAST MON MAY` | the last Monday in May | `xnys.memorial_day` |
| `2015-06-09` | one calendar day (ISO) | |
| `[2 DEC : 3 DEC]` | every day from 2 to 3 December, both included | `ae.national_day` |
| `[1 ZHENGYUE - 1D : 1D : 1 ZHENGYUE + 2D]` | a series: start, step, end | `cn.chinese_new_year` |
| `[7 JAN : 1M : 7 DEC]` | the 7th of every month | `nyfed.consumer_expectations` |
| `a, b, c` | several dates in one rule | `msci.rebalance` |

Units of periods: `D` days, `W` weeks, `M` months, `Q` quarters, `Y` years, `B` business days.

### Shifts and rolls

Shifts follow the date and apply left to right.

| Shift | Meaning | Example |
|---|---|---|
| `+ 1D`, `- 1D` | add or subtract a period | `@xnys.thanksgiving + 1D` (the Friday after Thanksgiving) |
| `> NEXT WED` | the first Wednesday **strictly after** the date | `25 JAN > NEXT WED` (the Wednesday on or after 26 January) |
| `> PREV MON` | the first Monday strictly before | `25 MAY > PREV MON` (Victoria Day, the Monday before 25 May) |
| `> PREV 2ND SAT` | the second Saturday strictly before | `@fed.fomc_decision > PREV 2ND SAT` |
| `> NEXT BD` | the next business day strictly after | `@issuer.allianz.agm > NEXT BD ON exchange:XETR` |
| `> PRECEDING` | if the date is not a business day, the business day before | `1 THU FEB > PRECEDING ON country:GB` |
| `> FOLLOWING` | if it is not a business day, the business day after | `[7 JAN : 1M : 7 DEC] > FOLLOWING` |
| `> MODFOLLOWING`, `> MODPRECEDING` | as above, but not into another month | |
| `> EOM` | the last day of the month (also `BOM`, `EOQ`, `EOY`, ...) | `1 FEB > EOM > PRECEDING` (the last business day of February) |
| `ON scope` | which calendar the business days come from; without it, the rule's own scope | `> PRECEDING ON central_bank:FED` |

Because `NEXT` is strictly after, "the Wednesday on or after the 16th" is written `16 JAN - 1D > NEXT WED`.

### Observance shifts

A brace block right after a date moves it only when it falls on the named weekdays; other weekdays leave it alone.

| Expression | Meaning | Example |
|---|---|---|
| `{SAT - 1D, SUN + 1D}` | Saturday to the Friday before, Sunday to the Monday after | US Independence Day at the NYSE |
| `{SUN + 1D}` | only a Sunday moves; a Saturday holiday is lost | Federal Reserve holidays |
| `{SAT + 2D, SUN + 1D}` | the weekend holiday moves to Monday | `gb.new_years_day` |
| `{SAT + 2D, SUN + 2D}` | Christmas and Boxing Day: both move past the weekend without colliding | `gb.christmas_day`, `gb.boxing_day` |
| `{FRI - 1D, SAT + 1D}` | for a Friday-Saturday weekend | `sa.national_day` |

### Lunar calendars and moon sighting

Month names of other calendars work like Gregorian ones:

- **Hijri (Islamic):** `MUHARRAM` ... `RAMADAN`, `SHAWWAL`, `DHULQADAH`, `DHULHIJJAH`. Plain `1 SHAWWAL` uses the
  Umm al-Qura calendar (Saudi Arabia's computed calendar).
- **Hebrew:** `TISHRI`, `HESHVAN`, ..., `ADAR`, `ADARII`, `NISAN`, ... (`[10 TISHRI - 1D : 1D : 10 TISHRI]` is
  Yom Kippur and its eve, `xtae.yom_kippur`).
- **Chinese:** `ZHENGYUE` (first month) ... `LAYUE` (twelfth). `1 ZHENGYUE` is Chinese New Year.

A lunar date that falls twice in a Gregorian year gives both (`1 RAMADAN` in 2030: 5 January and 26 December).

Where the real date depends on an actual sighting of the new moon, known only on the evening before, a rule gives
the alternatives: `ONE OF { 1 RAMADAN + 29D AS ramadan_29, 1 RAMADAN + 30D AS ramadan_30 }` (Eid al-Fitr,
`sa.eid_al_fitr`) is the day after a 29-day or a 30-day Ramadan, counted from the Umm al-Qura start of Ramadan.
Exactly one of them happens; each date is a projection under its name (`Occurrence.scenario`) until the authority's
announcement replaces them.

### Conditions

`IF` keeps a date only when the condition holds. Here it is used for weekdays:

```
rule xnys.christmas_eve_early_close for christmas_eve_early_close in exchange:XNYS = 24 DEC IF MON TUE WED THU
  with kind = short_day, session 09:30 - 13:00 tz "America/New_York" ...
```

The language also has `IF HOLIDAY`, `IF NOT ...`, `AND`/`OR` and `EVERY 4Y FROM 1965`, unused in these files.

### References to other rules

`@key` stands for all dates of another rule in the same year, in any file of this directory:

```
rule hkma.base_rate for hkma_base_rate in central_bank:HKMA = @fed.fomc_decision + 1D ...
rule sama.rate_decision for sama_rate_decision in central_bank:SAMA = @fed.fomc_decision ...
```

The Gulf and Hong Kong currencies are pegged to the US dollar, so their rate decisions follow the Fed's.
Unknown or circular references are errors when the files are loaded.

### Announced dates

Announced dates are data, given to the evaluator (section 5), not written in the rule files. A rule projects only
where there is a pattern; the Bank of England's rule covers four of its eight meetings:

```
rule boe.mpc_decision for boe_decision in central_bank:BOE
  = 1 THU FEB > PRECEDING ON country:GB, 1 THU NOV > PRECEDING ON country:GB,
    3 THU MAR > PRECEDING ON country:GB, 3 THU DEC > PRECEDING ON country:GB
```

For a year with announced dates under the rule's key, those dates replace the rule's; in the other years the rule's
dates are marked as projected. Events without any pattern (GDP releases, earnings dates, AGMs, one-off closures) have
no rule, only a comment naming the key and the source of their dates:

```
# No rule: the dates are only announced (bea_release_schedule); they are data for the evaluator, under the key
# bea.gdp.
```

A rule can still refer to such a key: `@issuer.allianz.agm > NEXT BD ON exchange:XETR` reads the announced AGM dates.

### Kinds

| Kind | Effect on the day |
|---|---|
| `closed` (default) | the market, system or office is shut; the day is not a business day |
| `short_day` | open with shortened or special hours; the `session` gives them |
| `working_day` | a weekend day that is worked (such as Russian and Chinese make-up days, which are only announced, so none in these files); it becomes a business day |
| `event` | a dated event: a decision, a release, minutes, an AGM, an index review; the day stays as it is |

The language also has `non_working`, `paid_no_rest` and `tourist`, unused here.

### Other attributes

- `session 09:30 - 13:00 tz "America/New_York"`: local trading hours. For a single announcement time the start
  equals the end (`session 14:30 - 14:30 tz "Australia/Sydney"`, the RBA's decision).
- `precision = month`: only the month is reliable; the day is a representative estimate (for example
  `bnm.mpc_decision`).
- `note "..."`: free text kept with each date.

### Validity and citations

- `valid from 1998-01-01`, `valid to 2024-12-31`: the rule applies only between these days (both inclusive).
- `cites "<source name>" [art "<article>"] [url "<url>"] label <label>`: the source. `art` names the legal article
  (`cites "Einigungsvertrag (Unification Treaty), Germany" art "2" label official` for German Unity Day). The label
  says what kind of source it is: `official` (the authority itself; every citation in these files),
  `benchmark` (a widely used reference set by others), `practice` (market practice), `secondary` (a report about
  it). A few rules have no citation because the catalog names none; their comments say so.

## 3. weekends.rules

A weekend day is never a business day. A scope without a line in `weekends.rules` has the weekend Saturday and
Sunday. The file lists the exceptions, mostly in the Gulf and the Middle East:

```
weekend country:AE = {FRI, SAT};
weekend country:AE = {SAT, SUN} from 2022-01-01;
weekend exchange:XTAE = {SAT, SUN} from 2026-01-05;   # Tel Aviv Stock Exchange: Mon-Fri since 5 Jan 2026
```

`from` gives the first day of a regime; the earlier regime ends there. In holidays-events a central bank or payment
system without its own regime takes its country's; date-parser has no parent scopes, so those scopes (for example
`central_bank:SAMA`, `settlement_system:SARIE`) are listed again with their country's regime. Comments in brackets
are the catalog's own remarks, including switch dates still to be verified (Oman, Algeria).

## 4. Files

| File | Covers | Rules |
|---|---|---|
| `us.rules` | NYSE holidays and early closes, Federal Reserve holidays (K.8), US bond market (SIFMA), FOMC decisions, minutes and press conferences, Fed blackout, BLS jobs report, New York Fed consumer expectations survey | 41 |
| `uk_ch.rules` | Bank holidays of England and Wales (most also apply in Northern Ireland), Bank of England decisions, UK CPI (ONS), SIX Swiss Exchange, Swiss payment systems SIC and euroSIC, Swiss National Bank | 41 |
| `apac_other.rules` | Rate decisions of 11 Asia-Pacific central banks, China's Spring Festival, Yom Kippur at Tel Aviv, MSCI quarterly reviews, BOJ-NET and RITS closing days | 23 (24 declarations) |
| `de_ez.rules` | TARGET2 (euro payment system), Bundesbank, Xetra, German nationwide and regional holidays, Eurex quarterly expiry | 30 |
| `cz.rules` | Czech National Bank rate decisions (Czech holidays come from libraries, not rules) | 1 |
| `gcc.rules` | Islamic holidays and national days of Saudi Arabia and the UAE, Gulf central-bank decisions following the Fed | 11 (12 declarations) |
| `issuers.rules` | AGMs with a pattern (Lufthansa, LVMH) and ex-dividend days of four listed companies | 6 |
| `br.rules` | Days on which Brazil's central bank publishes no PTAX dollar fixing | 16 |
| `ca.rules` | Closing days of Lynx, Canada's large-value payment system; Bank of Canada rate announcements (announced only) | 10 |
| `ru_cis.rules` | Russian non-working holidays, Bank of Russia key-rate meetings | 9 |
| `weekends.rules` | Weekend regimes that are not Saturday-Sunday | 0 (31 weekend lines) |
| **Total** | | **188** |

The 43 events whose dates are only ever announced have no rule; each has a comment with its key and source.

Some files refer to rules in others (`@fed.fomc_decision` from `us.rules` is used in `apac_other.rules` and
`gcc.rules`; `msci.rebalance` needs the NYSE holidays; the ex-dividend rules need the Xetra calendar), so load
all files together.

## 5. Evaluating the rules in Python

The evaluator is `lark_schedule.catalog` in `../date-rule-lark` (parsed by `lark_schedule.rules`; `date-rule-antlr-python` has the same). Run from that directory:

```python
from datetime import date
from pathlib import Path
from lark_schedule.rules import parse_rules

rules_dir = Path("../holidays-events")
text = "\n".join(p.read_text() for p in sorted(rules_dir.glob("*.rules")))

# the AGMs have no rule, only announced dates; the ex-dividend rules refer to them
agms = {k: [] for k in ("issuer.deutsche_bank.agm", "issuer.allianz.agm", "issuer.commerzbank.agm")}
catalog = parse_rules(text, announcements=agms)
for o in catalog.holidays("exchange:XNYS", 2027):
    print(o.date, o.rule, o.kind)

# The Bank of England publishes its meeting dates: they replace the projection for that year.
catalog = parse_rules(text, announcements={**agms, "boe.mpc_decision": [date(2027, 2, 4), date(2027, 3, 18)]})
for o in catalog.holidays("central_bank:BOE", 2027):
    print(o.date, o.rule, "projected" if o.projected else "announced")
for o in catalog.holidays("central_bank:BOE", 2028):
    print(o.date, o.rule, "projected" if o.projected else "announced")
```

Save the snippet as `example.py` in `../date-rule-lark` and run it there:

```
cd ../date-rule-lark && PYTHONPATH=src .venv/bin/python example.py   # or: uv run python example.py
```

Output (shortened):

```
2027-01-01 xnys.new_years_day closed
2027-01-18 xnys.mlk_day closed
...
2027-11-26 xnys.day_after_thanksgiving_early_close short_day
2027-12-24 xnys.christmas_day closed
2027-02-04 boe.mpc_decision announced
2027-03-18 boe.mpc_decision announced
2028-02-03 boe.mpc_decision projected
2028-03-16 boe.mpc_decision projected
2028-11-02 boe.mpc_decision projected
2028-12-21 boe.mpc_decision projected
```

- `parse_rules(text, announcements={key: [date, ...]})` loads the rules. Announcements are data, not rules: the
  dates an authority published under a key. For a rule's key they replace the rule's dates in each year that has
  some; a key without a rule (an AGM) holds announced dates only, which other rules read with `@key`.
- `catalog.holidays(scope, year)` returns every date of the rules in that scope as `Occurrence` records with `date`,
  `rule`, `event_class`, `kind`, `precision`, `session`, `citations`, `projected` (true for the dates of `ONE OF`
  and for the dates of a rule with announcements in a year without one) and `scenario` (the name of the `ONE OF` alternative). Events (`kind = event`) are included; filter on `kind`
  for closing days only.
- Loading fails on an unknown or circular reference (a key without a rule must be among the announcements) and on
  versions of a rule that overlap in time.

## 6. Verification

Every rule was compared day by day for the years **2021 to 2031** with the projections of the holidays-events rule
engine (the same catalog rule, evaluated by holidays-events itself), the
scenario names of the moon-sighting rules included.

| Files | Rules | Agree | Differ |
|---|---|---|---|
| `us.rules` | 40 | 40 | 0 |
| `uk_ch.rules` | 41 | 41 | 0 |
| `apac_other.rules` | 23 | 23 | 0 |
| `de_ez.rules`, `cz.rules` | 31 | 31 | 0 |
| `issuers.rules` | 6 | 6 | 0 |
| `gcc.rules` | 11 | 11 | 0 |
| `br.rules`, `ca.rules` | 26 | 26 | 0 |
| `ru_cis.rules` | 9 | 9 | 0 |
| **Total** | **187** | **187** | **0** |

`fed.fomc_press_conference`, added later, is not in the comparison; its dates are those of `fed.fomc_decision`.

Checked again on 2026-11-20 against the catalog of 2026-11-19: the 184 rules with dates in 2021 to 2031 agree day
by day (the other three wait for an announced AGM); the only rule here without one there is the press conference.
The catalog had added one announced-only event since (`boc.rate_announcement`, now in `ca.rules`) and new sources
for the two Ramadan sessions. The weekends of Bahrain and of Zahav (Israel's RTGS), also new there, are not in
`weekends.rules`, which only lists scopes these files use.

Equivalent but indirect forms (both engines agree, the comments explain them):

- The January jobs report (`bls.employment_situation`) is `3 JAN > NEXT FRI`, a closed form of the BLS's
  "early-January releases move one week later" adjustment.
- The German ex-dividend days use the Xetra calendar; holidays-events uses the issuer's calendar (weekdays only).
  The results are the same for 2021-2031 but would differ if a day after an AGM were a Xetra holiday.
- `> PRECEDING` on central-bank scopes without closing-day rules (Eurex, CNB, the Asia-Pacific banks) only skips
  weekends, as in holidays-events.
- "On or after a weekday" is written `16 JAN - 1D > NEXT WED` (`NEXT` is strictly after), which is exact.

## 7. Gaps in the language, and how they were closed

Found while translating (2026-11-14) and closed on 2026-11-16:

- **Moon-sighting scenarios:** `ONE OF { expr AS name, ... }` (rule files) gives alternatives of which exactly one
  happens, each under its name; it replaces `BY SIGHTING` in the five Gulf rules.
- **Hijri month length:** not needed any more; the alternatives count from the Umm al-Qura start of the previous
  month (`1 RAMADAN + 29D`, `+ 30D`).
- **A lunar date twice in a Gregorian year** gives both dates instead of an error (all thirteen implementations).
- **Ranges from each occurrence:** `[@e > PREV 2ND SAT : @e + 1D]` pairs both ends per occurrence of `@e`
  (all thirteen implementations); `fed.blackout` is one range per FOMC decision.
- **Day of the month:** `IF DAY 1..3`, `IF DAY 8..14 APR` (all thirteen implementations); Zurich's Sechseläuten uses it.
- **Monthly series with a weekday shift:** this already worked (`[16 JAN : 1M : 16 DEC] - 1D > NEXT WED`, `ons.cpi`).
- **A single time of day:** `session 14:30` (rule files).
- **Kinds:** `special_session` (a short session on a day that stays closed, the NSE's Muhurat session) and
  `changed_hours` (other trading hours, Ramadan sessions) (rule files).

Closed without a language change:

- **Precision per date:** a rule whose precision changes is split into versions with `valid from/to`
  (`bok.mpb_decision` would be).
- **Conditional events** (a German ex-dividend day only if the AGM resolves a dividend) are data: the authority's
  announcement, not a rule.
