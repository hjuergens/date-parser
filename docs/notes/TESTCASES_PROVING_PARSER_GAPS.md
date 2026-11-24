# Test Cases Proving Parsers Are Not Working As Expected

This document provides test cases that demonstrate the parsers in `date-rule-combinators` do not work as expected according to the natty.txt specification.

## Overview

The file `/home/juergens/workspace/date-parser/date-rule-combinators/src/site/natty.txt` defines various date formats that should be supported:
- Formal dates (integer representations)
- Relaxed dates (natural language representations)
- Relative dates (relative to current date)
- Date alternatives (list of date options)
- Prefixes (modifiers for dates)

## Test Case Files Created

### 1. Test Resource File
**File:** `/home/juergens/workspace/date-parser/date-rule-combinators/src/test/resources/natty_dates.txt`

Contains all the date formats from natty.txt that should be tested.

### 2. Test Class File
**File:** `/home/juergens/workspace/date-parser/date-rule-combinators/src/test/scala/de/juergens/text/parsers/NattyDateParserGapsTest.scala`

Contains ScalaTest test cases that will fail, proving the parsers don't work as expected.

## Specific Failures Identified

### 1. Formal Dates - REGEX TOO RESTRICTIVE

**Current Implementation:**
```scala
def date : Parser[Date] = RegexParser("""(\d{1,2})\.(\d{1,2})\.(\d{2,4})""".r) ^^ ...
```

**Only supports:** `dd.MM.yyyy` format (e.g., `23.07.2015`)

**Fails on:**
- `1978-01-28` - dash separator
- `1984/04/02` - slash separator
- `1/02/1980` - single-digit month with slash
- `2/28/79` - 2-digit year with slash

**Test Cases:**
```scala
parseAll(date, "1978-01-28")  // FAILS
parseAll(date, "1984/04/02")   // FAILS
parseAll(date, "1/02/1980")    // FAILS
parseAll(date, "2/28/79")      // FAILS
```

### 2. Relaxed Dates - NOT IMPLEMENTED

**Current Implementation:**
The `monthDay` parser only handles:
- `December 3rd` (month name + ordinal)
- `fourth of July` (ordinal + "of" + month name)

**Fails on:**
- `The 31st of April in the year 2008` - complex natural language
- `Fri, 21 Nov 1997` - day of week + numeric date
- `Jan 21, '97` - month + numeric day + 2-digit year with apostrophe
- `Sun, Nov 21` - day of week + month + numeric day
- `jan 1st` - lowercase month name
- `february twenty-eighth` - month + cardinal number (not ordinal)

**Test Cases:**
```scala
parseAll(monthDay, "the 31st of april in the year 2008")  // FAILS
parseAll(adjuster, "fri, 21 nov 1997")                     // FAILS
parseAll(monthDay, "jan 21, '97")                         // FAILS
parseAll(adjuster, "sun, nov 21")                         // FAILS
parseAll(monthDay, "jan 1st")                              // FAILS (case)
parseAll(monthDay, "february twenty-eighth")               // FAILS (cardinal)
```

### 3. Date Alternatives - NOT IMPLEMENTED

**Current Implementation:**
The `daysOfWeek` parser handles alternatives for days of week:
```scala
def daysOfWeek : Parser[WeekDayPredicate] = repsep(dayOfWeek, "or" | ",") ^^
  { new WeekDayPredicate(_ :_*) }
```
But this is for predicates, not for adjusters or dates.

**Fails on:**
- `next wed or thurs` - day of week alternatives as adjuster
- `oct 3rd or 4th` - date alternatives

**Test Cases:**
```scala
parseAll(adjuster, "next wed or thurs")  // FAILS
parseAll(monthDay, "oct 3rd or 4th")     // FAILS
```

### 4. Relative Dates - PARTIALLY WORKING

**Working:**
- `next thursday` ✓
- `last wednesday` ✓
- `today` ✓
- `tomorrow` ✓
- `yesterday` ✓
- `next week` ✓
- `next month` ✓
- `next year` ✓
- `three weeks ago` ✓

**Failing:**
- `3 days from now` ❌ - The shifter parser doesn't handle "now" after "from"

**All these work with the `adjuster` parser except where noted.**

### 5. Prefixes - WORKING

**Working:**
- `day after` ✓
- `the day before` ✓
- `the monday after` ✓
- `the monday before` ✓
- `2 fridays before` ✓
- `4 tuesdays after` ✓

**All these work with the `adjuster` parser.**

## Summary Table

| Category | Expected | Working | Status | Test Cases |
|----------|----------|---------|--------|------------|
| Formal dates (dots) | Yes | Yes | ✅ | `28.01.1978` |
| Formal dates (dashes) | Yes | No | ❌ | `1978-01-28` |
| Formal dates (slashes) | Yes | No | ❌ | `1984/04/02`, `1/02/1980`, `2/28/79` |
| Relaxed dates (complex) | Yes | No | ❌ | `The 31st of April in the year 2008` |
| Relaxed dates (weekday + date) | Yes | No | ❌ | `Fri, 21 Nov 1997`, `Sun, Nov 21` |
| Relaxed dates (month + day + year) | Yes | No | ❌ | `Jan 21, '97` |
| Relaxed dates (lowercase) | Yes | No | ❌ | `jan 1st` |
| Relaxed dates (cardinal) | Yes | No | ❌ | `february twenty-eighth` |
| Date alternatives (weekdays) | Yes | No | ❌ | `next wed or thurs` |
| Date alternatives (dates) | Yes | No | ❌ | `oct 3rd or 4th` |
| Relative dates (basic) | Yes | Yes | ✅ | `next thursday`, `last wednesday`, etc. |
| Relative dates (with "from now") | Yes | No | ❌ | `3 days from now` |
| Prefixes | Yes | Yes | ✅ | All prefixes work |

## How to Run the Tests

To verify these failures, run:

```bash
cd /home/juergens/workspace/date-parser/date-rule-combinators
# Run the specific test class
# The tests that should FAIL are marked with assertions that check for failure
```

## Required Fixes

To make the parsers work as expected, the following changes are needed:

1. **Update `date` parser regex** to support multiple separators:
   ```scala
   def date : Parser[Date] = RegexParser("""(\d{1,2})[./-](\d{1,2})[./-](\d{2,4})""".r) ^^ ...
   ```

2. **Add relaxed date parsers** for:
   - Day of week + date combinations
   - Numeric dates with month names
   - Cardinal numbers for days
   - Case-insensitive month names
   - Year formats with apostrophes

3. **Add date alternatives parser** that works with:
   - Day of week alternatives (for adjusters)
   - Date alternatives (for monthDay)

4. **Enhance existing parsers** to handle:
   - "from now" pattern
   - Case-insensitive input

## Conclusion

The test cases in `NattyDateParserGapsTest.scala` will **fail** for the identified gaps, proving that the parsers are not working as expected according to the natty.txt specification. Running these tests will demonstrate the specific functionality that needs to be implemented.
