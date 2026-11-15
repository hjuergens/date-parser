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

- **An announcement always wins.** Once the authority publishes its dates for a year, they replace the projection
  for that year completely, even if the rule would have given other days.
- **A projection is only as good as its pattern.** Fixed holidays are certain. Meeting and release dates are
  projected only where the authority states the pattern itself, or where an observed pattern hit the authority's
  own dates exactly at least 80% of the time over at least two years. Where no pattern is good enough, the rule has
  no dates until the authority publishes.

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

`1 SHAWWAL BY SIGHTING` (Eid al-Fitr, `sa.eid_al_fitr`) says that the real date depends on an actual sighting of
the new moon and is only known a day or so before. The rule computes the Umm al-Qura date and marks every such date
as a projection; the authority's announcement replaces it.

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

### Announced rules: `ANNOUNCED BY ... ELSE ...`

```
rule boe.mpc_decision for boe_decision in central_bank:BOE
  = ANNOUNCED BY boe_mpc_dates
    ELSE 1 THU FEB > PRECEDING ON country:GB, 1 THU NOV > PRECEDING ON country:GB,
         3 THU MAR > PRECEDING ON country:GB, 3 THU DEC > PRECEDING ON country:GB
```

`ANNOUNCED BY boe_mpc_dates` names the authority's publication (a source key of the holidays-events catalog). For
a year in which it has published, its dates are used and the `ELSE` part is ignored. For other years the `ELSE`
dates are used and marked as projected. The `ELSE` part often covers only the meetings with a reliable pattern
(here four of the Bank's eight). Without `ELSE` the rule has no dates at all until the authority publishes:

```
rule bea.gdp for us_gdp in statistics_office:BEA = ANNOUNCED BY bea_release_schedule ...
```

### Kinds

| Kind | Effect on the day |
|---|---|
| `closed` (default) | the market, system or office is shut; the day is not a business day |
| `short_day` | open with shortened or special hours; the `session` gives them |
| `working_day` | a weekend day that is worked (Russian and Chinese make-up days); it becomes a business day |
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
| `us.rules` | NYSE holidays and early closes, Federal Reserve holidays (K.8), US bond market (SIFMA), FOMC decisions and minutes, Fed blackout, BLS (CPI, jobs report), BEA (GDP, personal income), New York Fed consumer expectations survey, Treasury auctions | 45 |
| `uk_ch.rules` | Bank holidays of England and Wales (most also apply in Northern Ireland), Bank of England decisions, UK CPI (ONS), SIX Swiss Exchange, Swiss payment systems SIC and euroSIC, Swiss National Bank | 40 |
| `apac_other.rules` | Rate decisions and minutes of 13 Asia-Pacific central banks, Japan's GDP, China's Spring Festival and make-up days, NSE Muhurat session, Yom Kippur at Tel Aviv, MSCI quarterly reviews, BOJ-NET and RITS closing days | 34 (35 declarations) |
| `de_ez.rules` | TARGET2 (euro payment system), Bundesbank, Xetra, German nationwide holidays, Eurex quarterly expiry, euro-area and German inflation releases, ECB consumer expectations survey | 31 |
| `cz.rules` | Czech National Bank rate decisions (Czech holidays come from libraries, not rules) | 1 |
| `gcc.rules` | Islamic holidays and national days of Saudi Arabia and the UAE, Eid closures of SAMA, SARIE and Tadawul, Gulf central-bank decisions following the Fed, Ramadan sessions in Qatar and Egypt | 17 (18 declarations) |
| `issuers.rules` | Results, AGMs and ex-dividend days of six listed companies | 18 |
| `br.rules` | Days on which Brazil's central bank publishes no PTAX dollar fixing | 13 |
| `ca.rules` | Closing days of Lynx, Canada's large-value payment system | 10 |
| `ru_cis.rules` | Russian non-working holidays, transferred days off and make-up working days, Bank of Russia key-rate meetings | 11 |
| `weekends.rules` | Weekend regimes that are not Saturday-Sunday | 0 (31 weekend lines) |
| **Total** | | **220** |

Some files refer to rules in others (`@fed.fomc_decision` from `us.rules` is used in `apac_other.rules` and
`gcc.rules`; `msci.rebalance` needs the NYSE holidays; the ex-dividend rules need the Xetra calendar), so load
all files together.

## 5. Evaluating the rules in Python

The evaluator is `lark_schedule.rules` in `../date-rule-lark`. Run from that directory:

```python
from datetime import date
from pathlib import Path
from lark_schedule.rules import parse_rules

rules_dir = Path("../holidays-events")
text = "\n".join(p.read_text() for p in sorted(rules_dir.glob("*.rules")))

book = parse_rules(text)
for o in book.holidays("exchange:XNYS", 2027):
    print(o.date, o.rule, o.kind)

# The Bank of England publishes its meeting dates: they replace the projection for that year.
book = parse_rules(text, announcements={"boe.mpc_decision": [date(2027, 2, 4), date(2027, 3, 18)]})
for o in book.holidays("central_bank:BOE", 2027):
    print(o.date, o.rule, "projected" if o.projected else "announced")
for o in book.holidays("central_bank:BOE", 2028):
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

- `parse_rules(text, announcements={rule_key: [date, ...]})` loads the rules. Announcements are data, not rules:
  the dates an authority published for a rule; for each year that has some, they replace the rule's dates.
- `book.holidays(scope, year)` returns every date of the rules in that scope as `Occurrence` records with `date`,
  `rule`, `event_class`, `kind`, `precision`, `session`, `citations` and `projected` (true for `BY SIGHTING` dates
  and for `ELSE` dates of a year without an announcement). Events (`kind = event`) are included; filter on `kind`
  for closing days only.
- Loading fails on an unknown or circular reference and on versions of a rule that overlap in time.

## 6. Verification

Every rule was compared day by day for the years **2021 to 2031** with the projections of the holidays-events rule
engine (the same catalog rule, evaluated by holidays-events itself; rules without `ELSE` give no dates in either).

| Files | Rules | Agree | Differ |
|---|---|---|---|
| `us.rules` | 45 | 45 | 0 |
| `uk_ch.rules` | 40 | 40 | 0 |
| `apac_other.rules` | 34 | 34 | 0 |
| `de_ez.rules`, `cz.rules` | 32 | 32 | 0 |
| `issuers.rules` | 18 | 18 | 0 |
| `gcc.rules` | 17 | 12 | 5 |
| `br.rules`, `ca.rules` | 23 | 23 | 0 |
| `ru_cis.rules` | 11 | 11 | 0 |
| **Total** | **220** | **215** | **5** |

All five differences are moon-sighting rules in `gcc.rules`. holidays-events gives **two** candidate dates per year
for them, one if the previous Hijri month has 29 days and one if it has 30. date-parser gives **one**, the Umm
al-Qura date, which is always one of the two. date-parser never has a day that holidays-events lacks; it only lacks
the days of the other scenario.

| Rule | Missing days (2021-2031) |
|---|---|
| `sa.eid_al_fitr` | 11: the other Ramadan-length scenario, one per year |
| `sa.eid_al_adha` | 11: the other Dhu al-Qadah-length scenario |
| `sa.arafat_day` | 11: the other scenario of the previous month (the engine's default scenarios) |
| `ae.eid_al_fitr` | 6: 2023-04-24 (3 Shawwal after a 30-day Ramadan, version before 2025) and 30 Ramadan in the 30-day scenario in 2025, 2027, 2028, 2029 and 2031 |
| `ae.eid_al_adha` | 5: 12 Dhu al-Hijja in the 30-day Dhu al-Qadah scenario in 2023, 2024, 2025, 2027 and 2031 |

The missing day falls before the Umm al-Qura date in some years and after it in others, so no fixed offset can
reproduce it, and listing both days would declare two closures. In practice both engines are overridden by the
authority's announcement, which comes days before the holiday.

Equivalent but indirect forms (both engines agree, the comments explain them):

- The January jobs report (`bls.employment_situation`) is `3 JAN > NEXT FRI`, a closed form of the BLS's
  "early-January releases move one week later" adjustment.
- The German ex-dividend days use the Xetra calendar; holidays-events uses the issuer's calendar (weekdays only).
  The results are the same for 2021-2031 but would differ if a day after an AGM were a Xetra holiday.
- `> PRECEDING` on central-bank scopes without closing-day rules (Eurex, CNB, the Asia-Pacific banks) only skips
  weekends, as in holidays-events.

## 7. Known gaps in the language

Found while translating; each affected rule's comment explains its workaround.

- **No moon-sighting scenarios.** `BY SIGHTING` gives one date; there is no way to say "one of two days depending
  on whether the previous month has 29 or 30 days" (the five differences above).
- **No condition on Hijri month length.** A literal `30 RAMADAN` is an error in a 29-day month instead of yielding
  nothing; `29 RAMADAN BY SIGHTING + 1D` is used instead.
- **Series endpoints must be single dates.** A span from each occurrence of a multi-date rule cannot be written.
  `fed.blackout` ("second Saturday before each FOMC decision until the day after") lists 13 days as
  `@fed.fomc_decision > PREV 2ND SAT + kD`, k = 0 to 12, exact only for Wednesday decisions (which scheduled FOMC
  decisions are).
- **No condition on the day of the month** (such as "if it falls on 1-3 January, add 7 days"); see the January
  jobs report above.
- **No "on or after a weekday" shift.** `NEXT` is strictly after, so "the Wednesday on or after the 16th" becomes
  `16 JAN - 1D > NEXT WED`.
- **No monthly series of a fixed day plus a weekday shift**; `ons.cpi` spells out all twelve months.
- **No single time of day.** `session` needs a start and an end; single release or decision times are written as
  zero-length sessions (`14:30 - 14:30`) or in a `note` (ONS CPI at 07:00, the SNB assessment at 09:30).
- **Precision is per rule, not per date.** `bok.mpb_decision` mixes day-exact and month-only meetings and stays at
  day precision.
- **No kind for a special session on a closed day** (the NSE's Muhurat session) or for a month of changed trading
  hours (Ramadan); `short_day` is used.
- **No conditional events.** A German ex-dividend day happens only if the AGM resolves a dividend; the rules give
  the day every year and say so in a note.
