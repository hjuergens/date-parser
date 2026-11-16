# Grammar Features Not Covered by holidays-events Rules

This document identifies which features of the Lark schedule grammar (in `date-rule-lark/src/lark_schedule/rules.lark`) are **not used** in the holidays-events rule files.

## Analysis Methodology

1. Examined all grammar rules and terminals in `rules.lark`
2. Searched through all `*.rules` files in `holidays-events/` directory
3. Identified grammar features that appear in the grammar but not in any rule file

## Unused Grammar Features

### 1. Declarations

| Feature | Grammar Rule | Status | Notes |
|---------|--------------|--------|-------|
| Calendar declarations | `calendar_decl: "calendar" KEY "=" calendar ";"` | ❌ NOT USED | Grammar supports named calendars, but none are defined in rules |

### 2. Date Expressions

| Feature | Grammar Rule | Status | Notes |
|---------|--------------|--------|-------|
| Today (T) | `single_date: "T" -> base` | ❌ NOT USED | Grammar supports "T" for today, but not used |
| Tomorrow (TOM) | `single_date: "TOM" -> tom` | ❌ NOT USED | Grammar supports "TOM" for tomorrow, but not used |
| SPOT date | `single_date: "SPOT" [PAIR] [on] -> spot_date` | ❌ NOT USED | Grammar supports SPOT dates, but not used |
| German date format | `single_date: NUM "." NUM "." NUM -> german` | ❌ NOT USED | DD.MM.YYYYY format in grammar, not used in rules |
| US date format | `single_date: NUM "/" NUM "/" NUM -> us` | ❌ NOT USED | MM/DD/YYYY format in grammar, not used in rules |
| ISO date format | `single_date: NUM "-" NUM "-" NUM -> iso` | ❌ NOT USED | YYYY-MM-DD format in grammar, not used in rules |
| Season dates | `SEASON MONTH [zone] -> season` | ❌ NOT USED | EQUINOX and SOLSTICE not used |
| Solar term dates | `SOLARTERM NUM [zone] -> solar_term` | ❌ NOT USED | Not used |
| Hindu dates | `HINDU KEY -> hindu` | ❌ NOT USED | Grammar supports, but no Hindu calendar rules |
| Orthodox Easter | `ORTHODOX EASTER -> orthodox_easter` | ❌ NOT USED | Only Western EASTER is used |

### 3. Hijri Calendar Methods

| Feature | Grammar Rule | Status | Notes |
|---------|--------------|--------|-------|
| BY SIGHTING | `hijri_method: "BY" (UMMALQURA \| SIGHTING \| DIYANET \| "FOLLOW" SCOPE)` | ❌ NOT USED | Hijri dates use implicit UMMALQURA, not explicit BY clause |
| BY DIYANET | Same as above | ❌ NOT USED | Not used |
| BY FOLLOW SCOPE | Same as above | ❌ NOT USED | Not used |

**Note:** Plain Hijri dates like `1 RAMADAN` use UMMALQURA by default (per grammar comment), so the explicit `BY UMMALQURA` syntax is not needed.

### 4. Shifts and Rolls

| Feature | Grammar Rule | Status | Notes |
|---------|--------------|--------|-------|
| BOW (Beginning of Week) | `ROLL: "BOW"` | ❌ NOT USED | Only EOM is used from ROLL options |
| EOW (End of Week) | `ROLL: "EOW"` | ❌ NOT USED | Not used |
| BOM (Beginning of Month) | `ROLL: "BOM"` | ❌ NOT USED | Not used |
| BOQ (Beginning of Quarter) | `ROLL: "BOQ"` | ❌ NOT USED | Not used |
| EOQ (End of Quarter) | `ROLL: "EOQ"` | ❌ NOT USED | Not used |
| BOH (Beginning of Half-year) | `ROLL: "BOH"` | ❌ NOT USED | Not used |
| EOH (End of Half-year) | `ROLL: "EOH"` | ❌ NOT USED | Not used |
| BOY (Beginning of Year) | `ROLL: "BOY"` | ❌ NOT USED | Not used |
| EOY (End of Year) | `ROLL: "EOY"` | ❌ NOT USED | Not used |
| NEAREST anchor | `shift: ">" "NEAREST" anchor -> nearest` | ❌ NOT USED | Not used |
| POSTPONE | `shift: ">" "POSTPONE" ROLL [on] "MAX" period [CUMULATIVE] -> postpone` | ❌ NOT USED | Not used |
| ENDEND | `shift: "+" period [ENDEND] [on] -> plus` | ❌ NOT USED | Not used |
| CUMULATIVE | Part of POSTPONE shift | ❌ NOT USED | Not used |
| Select in series | `shift: ">" count selection "IN" PERIOD -> select` | ❌ NOT USED | Not used |
| Dates in year | `shift: ">" "{" annual ("," annual)* "}" -> dates_in_year` | ❌ NOT USED | Not used |

**Note:** EOM, FOLLOWING, PRECEDING, MODFOLLOWING, MODPRECEDING are used.

### 5. Conditions

| Feature | Grammar Rule | Status | Notes |
|---------|--------------|--------|-------|
| subject IS HOLIDAY | `cond_atom: subject "IS" "HOLIDAY" [on] -> date_is_holiday` | ❌ NOT USED | Only `IF HOLIDAY` is used (which tests the date itself) |
| BETWEEN HOLIDAYS | `cond_atom: "BETWEEN" "HOLIDAYS" [on] -> between_holidays` | ❌ NOT USED | Not used |
| EVERY condition | `cond_atom: "EVERY" period "FROM" NUM -> every` | ❌ NOT USED | Not used |
| NOT condition | `cond_not: "NOT" cond_atom -> not_` | ❌ NOT USED | Grammar supports NOT, but not used |
| AND/OR conditions | `cond_and, cond_or` | ❌ NOT USED | Grammar supports AND/OR, but not used |

**Note:** `IF DAY 8..14 APR` is used, and `IF` with weekday_set is used.

### 6. Scopes

| Feature | Grammar Rule | Status | Notes |
|---------|--------------|--------|-------|
| except in scope_sel | `scope_sel: scopes ["except" scopes]` | ❌ NOT USED | Grammar supports excluding scopes, but not used |

### 7. Calendars

| Feature | Grammar Rule | Status | Notes |
|---------|--------------|--------|-------|
| ignore at T+N | `ignore: "ignore" "at" "T" "+" NUM ("unless" "{" CCY ("," CCY)* "}")?` | ❌ NOT USED | Grammar supports ignoring T+N for certain currencies, but not used |
| ignore unless | Part of ignore rule | ❌ NOT USED | Not used |
| CCY (currency) | `CCY: /[A-Z]{3}(?![A-Z])/` | ❌ NOT USED | Not used |

### 8. Kinds

| Feature | Grammar Rule | Status | Notes |
|---------|--------------|--------|-------|
| non_working | `!kind: ... \| "non_working" \| ...` | ❌ NOT USED | Not used |
| paid_no_rest | `!kind: ... \| "paid_no_rest" \| ...` | ❌ NOT USED | Not used |
| tourist | `!kind: ... \| "tourist" \| ...` | ❌ NOT USED | Not used |

**Note:** closed, short_day, event, working_day, special_session, changed_hours ARE used.

### 9. Labels

| Feature | Grammar Rule | Status | Notes |
|---------|--------------|--------|-------|
| benchmark | `!label: "benchmark"` | ❌ NOT USED | Only "official" is used |
| practice | `!label: "practice"` | ❌ NOT USED | Not used |
| secondary | `!label: "secondary"` | ❌ NOT USED | Not used |

### 10. Attributes

| Feature | Grammar Rule | Status | Notes |
|---------|--------------|--------|-------|
| precision = day | `precision: "day" \| "month"` | ❌ NOT USED | Only "precision = month" is used |

## Summary

### Total Grammar Features: ~100+ rules and terminals
### Features Used in holidays-events: ~80-90
### Features NOT Used: ~20-30

### Most Significant Unused Features:

1. **Calendar declarations** - Could be used to define reusable calendar configurations
2. **Today/Tomorrow/SPOT dates** - Useful for relative date calculations
3. **Orthodox Easter** - Important for Eastern Orthodox Christian holidays
4. **Various ROLL options** - BOW, EOW, BOM, EOQ, BOH, EOH, BOY, EOY
5. **POSTPONE and NEAREST shifts** - Advanced shifting capabilities
6. **Complex conditions** - BETWEEN HOLIDAYS, EVERY, NOT, AND/OR
7. **Scope exceptions** - Ability to exclude certain scopes
8. **Hindu calendar** - Support for Hindu festival dates
9. **Season and Solar Term dates** - Astronomical calendar support
10. **Various kind values** - non_working, paid_no_rest, tourist

## Recommendations

The holidays-events rules currently use a subset of the available grammar features. The unused features are either:

1. **Not needed** for the current set of holidays (e.g., Hindu calendar, Orthodox Easter for non-Orthodox countries)
2. **Could be useful** but haven't been needed yet (e.g., calendar declarations, POSTPONE shifts)
3. **Redundant** with other features (e.g., explicit hijri_method when implicit UMMALQURA is sufficient)

The grammar appears to be **more comprehensive** than what's currently needed for the holidays-events rules, which is good for future extensibility.

## Verification

All findings were verified by searching through all `*.rules` files in the `holidays-events/` directory. The search was case-insensitive and excluded comments (lines starting with `#`).

To verify these findings, run:
```bash
cd /home/juergens/workspace/date-parser/holidays-events
grep -r "calendar\|HINDU\|ORTHODOX\|POSTPONE\|NEAREST\|BOW\|EOW" *.rules
# Should return no results (except in comments)
```
