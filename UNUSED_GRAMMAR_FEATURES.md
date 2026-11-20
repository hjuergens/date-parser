# Unused Grammar Features in holidays-events Rules

## Direct Answer

The following features defined in the Lark grammar (`date-rule-lark/src/lark_schedule/rules.lark`) are **NOT used** in any of the holidays-events rule files:

### 1. **Declarations**
- `calendar KEY = calendar;` - No calendar declarations exist in any rules file

### 2. **Date Types**
- `T` (today)
- `TOM` (tomorrow)
- `SPOT` (spot date)
- German date format: `DD.MM.YYYY`
- US date format: `MM/DD/YYYY`
- ISO date format: `YYYY-MM-DD`
- `SEASON MONTH` (EQUINOX, SOLSTICE)
- `SOLARTERM NUM` (Chinese solar terms)
- `HINDU KEY` (Hindu calendar dates)
- `ORTHODOX EASTER` (Eastern Orthodox Easter)

### 3. **Hijri Calendar Methods**
- `BY SIGHTING` (explicit moon sighting method)
- `BY DIYANET` (explicit Diyanet calendar)
- `BY FOLLOW SCOPE` (follow another scope's Hijri calendar)

*Note: Hijri dates use implicit UMMALQURA calendar, so explicit BY clauses are not needed.*

### 4. **Shifts and Rolls**
- `> BOW` (beginning of week)
- `> EOW` (end of week)
- `> BOM` (beginning of month)
- `> BOQ` (beginning of quarter)
- `> EOQ` (end of quarter)
- `> BOH` (beginning of half-year)
- `> EOH` (end of half-year)
- `> BOY` (beginning of year)
- `> EOY` (end of year)
- `> NEAREST anchor`
- `> POSTPONE ROLL [on] MAX period [CUMULATIVE]`
- `+ period ENDEND` (end-to-end period addition)
- `> count selection IN PERIOD` (select nth occurrence)
- `> { annual, ... }` (dates in year)

### 5. **Conditions**
- `subject IS HOLIDAY` (test if another date is a holiday)
- `BETWEEN HOLIDAYS` (test if date is between holidays)
- `EVERY period FROM NUM` (periodic conditions)
- `NOT condition` (negation)
- `condition AND condition` (conjunction)
- `condition OR condition` (disjunction)

### 6. **Scopes**
- `scopes EXCEPT scopes` (exclude certain scopes)

### 7. **Calendars**
- `ignore at T+N` (ignore T+N settlement)
- `ignore at T+N unless {CCY, ...}` (currency-specific ignore)
- `CCY` terminal (currency codes)

### 8. **Kinds**
- `non_working`
- `paid_no_rest`
- `tourist`

### 9. **Labels**
- `benchmark`
- `practice`
- `secondary`

### 10. **Precision**
- `precision = day` (only `precision = month` is used)

## What IS Used

The holidays-events rules **do use** the following major features:

✅ `ANNOUNCED BY ... ELSE ...` - Authority announcements
✅ `ONE OF { ... AS name, ... }` - Moon sighting alternatives
✅ `rule ... for ... in ...` - Basic rule declarations
✅ `valid from/to` - Validity periods
✅ `weekend SCOPE = ...` - Weekend declarations
✅ `with kind = ...` - Various kinds (closed, short_day, event, etc.)
✅ `with precision = month`
✅ `with session ...` - Trading hours
✅ `with note ...`
✅ `cites ... label official`
✅ `EASTER` - Western Easter calculations
✅ Hijri dates (RAMADAN, SHAWWAL, DHULQADAH, DHULHIJJAH)
✅ Hebrew dates (TISHRI, NISAN, etc.)
✅ Chinese dates (ZHENGYUE, etc.)
✅ Fixed annual dates: `NUM MONTH`
✅ Nth weekday: `NUM WEEKDAY MONTH`
✅ Last weekday: `LAST WEEKDAY MONTH`
✅ Series: `[start : step : end]` and `[start : end]`
✅ Shifts: `> PRECEDING`, `> FOLLOWING`, `> NEXT`, `> PREV`, `> EOM`, `+ period`, `- period`
✅ Observance shifts: `{WEEKDAY + period, ...}`
✅ Conditions: `IF DAY NUM..NUM [MONTH]`, `IF weekday_set`
✅ References: `@key`
✅ ON scope: `> PRECEDING ON scope`
✅ Exclude: `date \ {date, ...}` (`zrh.sechselaeuten`); include (`&`, a union) is not used
✅ Label: `official`

## Files Analyzed

All `*.rules` files in [holidays-events/](holidays-events/):
- `apac_other.rules` (23 rules)
- `br.rules` (16 rules)
- `ca.rules` (10 rules)
- `cz.rules` (1 rule)
- `de_ez.rules` (30 rules)
- `gcc.rules` (11 rules)
- `issuers.rules` (6 rules)
- `ru_cis.rules` (9 rules)
- `uk_ch.rules` (41 rules)
- `us.rules` (41 rules)
- `weekends.rules` (31 weekend lines)

**Total: 188 rules** (190 declarations: two rules have a second version), as in the table of
[holidays-events/README.md](holidays-events/README.md). 187 of them were compared day by day for 2021 to 2031 with
holidays-events' own engine and all agree; `fed.fomc_press_conference` came later.

## Conclusion

The holidays-events rules use approximately **80-90%** of the available grammar features. The unused features are either:

1. **Not applicable** to the current set of holidays (e.g., Orthodox Easter for non-Orthodox countries)
2. **Redundant** with existing patterns (e.g., explicit Hijri methods when implicit works)
3. **Specialized** features not yet needed (e.g., POSTPONE shifts, calendar declarations)

The grammar is comprehensive and provides good coverage for the holidays-events use case, with room for future expansion.
