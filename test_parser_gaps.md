# Parser Gaps Identified from natty.txt

This document identifies test cases from natty.txt that prove the parsers are not working as expected.

## Formal Dates - MISSING

The `date` parser in `DateParsers.scala` only handles dot-separated format `dd.MM.yyyy`:

```scala
def date : Parser[Date] = RegexParser("""(\d{1,2})\.(\d{1,2})\.(\d{2,4})""".r) ^^ ...
```

**Test Cases That FAIL:**
1. `1978-01-28` - dash-separated format not supported
2. `1984/04/02` - slash-separated format not supported
3. `1/02/1980` - slash-separated with single-digit month not supported
4. `2/28/79` - slash-separated with 2-digit year not supported

**Test Case That WORKS:**
- `23.07.2015` - dot-separated format works

**Solution:** The regex pattern needs to be updated to support multiple separator types:
```scala
def date : Parser[Date] = RegexParser("""(\d{1,2})[./-](\d{1,2})[./-](\d{2,4})""".r) ^^ ...
```

## Relaxed Dates - MISSING

The `monthDay` parser only handles:
- `December 3rd` (month name + ordinal)
- `fourth of July` (ordinal + "of" + month name)

**Test Cases That FAIL:**
1. `The 31st of April in the year 2008` - "The" prefix and "in the year" suffix not supported
2. `Fri, 21 Nov 1997` - day of week + numeric date not supported
3. `Jan 21, '97` - month + numeric day + 2-digit year with apostrophe not supported
4. `Sun, Nov 21` - day of week + month + numeric day not supported
5. `jan 1st` - lowercase month + ordinal not supported (case sensitivity issue)
6. `february twenty-eighth` - month + cardinal number not supported (only ordinal supported)

**Solution:** Need parsers for:
- Day of week + date combinations
- Numeric dates with month names
- Cardinal numbers for days (twenty-eighth)
- Case-insensitive month names
- Year suffixes like "'97"

## Relative Dates - NEED VERIFICATION

**Test Cases to Verify:**
1. `3 days from now` - Check if "from now" pattern works
2. `three weeks ago` - Check if "weeks ago" pattern works

The `shifter` parser handles `period ~ ("from" | direction)` where direction can be "ago", but need to verify "from now" works.

## Date Alternatives - MISSING

**Test Cases That FAIL:**
1. `next wed or thurs` - The `daysOfWeek` parser exists but is for predicates, not adjusters
2. `oct 3rd or 4th` - No parser for date alternatives

**Solution:** Need to add alternative parsing support for:
- Day of week alternatives: `next wed or thurs`
- Date alternatives: `oct 3rd or 4th`

## Summary of Missing Functionality

| Category | Expected Support | Current Support | Status |
|----------|-----------------|-----------------|---------|
| Formal dates (dash/slash) | Yes | No | ❌ FAIL |
| Relaxed dates (natural language) | Yes | No | ❌ FAIL |
| Date alternatives | Yes | No | ❌ FAIL |
| Relative dates (basic) | Yes | Partial | ⚠️ VERIFY |
| Prefixes | Yes | Yes | ✅ PASS |

## Recommended Test Cases to Add

Create a test file with these cases that should fail with the current implementation:

```
# Formal dates - should fail
1978-01-28
1984/04/02
1/02/1980
2/28/79

# Relaxed dates - should fail
The 31st of April in the year 2008
Fri, 21 Nov 1997
Jan 21, '97
Sun, Nov 21
jan 1st
february twenty-eighth

# Date alternatives - should fail
next wed or thurs
oct 3rd or 4th
```

These test cases will prove that the parsers are not working as expected according to the natty.txt specification.
