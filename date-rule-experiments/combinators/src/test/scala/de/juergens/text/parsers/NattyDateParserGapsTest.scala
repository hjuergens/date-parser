package de.juergens.text.parsers

import java.time.LocalDate

import de.juergens.text.{DateParsers, DateRuleParsers}
import org.scalatest.funsuite.AnyFunSuite
import org.testng.Assert._

/**
  * Test cases that prove parsers are not working as expected according to natty.txt
  */
class NattyDateParserGapsTest extends AnyFunSuite {

  val dateParsers = new DateRuleParsers with DateParsers

  // ============================================================================
  // FORMAL DATES - Currently only dot-separated format is supported
  // ============================================================================

  test("Formal date with dashes should FAIL: 1978-01-28") {
    val result = dateParsers.parseAll(dateParsers.date, "1978-01-28")
    assert(!result.successful, "Parser should not succeed with dash-separated format")
  }

  test("Formal date with slashes should FAIL: 1984/04/02") {
    val result = dateParsers.parseAll(dateParsers.date, "1984/04/02")
    assert(!result.successful, "Parser should not succeed with slash-separated format")
  }

  test("Formal date with single-digit month should FAIL: 1/02/1980") {
    val result = dateParsers.parseAll(dateParsers.date, "1/02/1980")
    assert(!result.successful, "Parser should not succeed with single-digit month")
  }

  test("Formal date with 2-digit year should FAIL: 2/28/79") {
    val result = dateParsers.parseAll(dateParsers.date, "2/28/79")
    assert(!result.successful, "Parser should not succeed with 2-digit year")
  }

  test("Formal date with dots should WORK: 28.01.1978") {
    val result = dateParsers.parseAll(dateParsers.date, "28.01.1978")
    assert(result.successful, "Parser should succeed with dot-separated format")
    assertEquals(result.get, LocalDate.of(1978, 1, 28))
  }

  // ============================================================================
  // RELAXED DATES - Currently not supported
  // ============================================================================

  test("Relaxed date: The 31st of April in the year 2008 should FAIL") {
    val result = dateParsers.parseAll(dateParsers.monthDay, "the 31st of april in the year 2008")
    assert(!result.successful, "Parser should not succeed with 'The ... in the year' format")
  }

  test("Relaxed date: Fri, 21 Nov 1997 should FAIL") {
    // Try different parsers
    val result1 = dateParsers.parseAll(dateParsers.adjuster, "fri, 21 nov 1997")
    val result2 = dateParsers.parseAll(dateParsers.monthDay, "fri, 21 nov 1997")
    assert(!result1.successful && !result2.successful, 
      "No parser should succeed with 'Fri, 21 Nov 1997' format")
  }

  test("Relaxed date: Jan 21, '97 should FAIL") {
    val result = dateParsers.parseAll(dateParsers.monthDay, "jan 21, '97")
    assert(!result.successful, "Parser should not succeed with apostrophe year format")
  }

  test("Relaxed date: Sun, Nov 21 should FAIL") {
    val result = dateParsers.parseAll(dateParsers.adjuster, "sun, nov 21")
    assert(!result.successful, "Parser should not succeed with day of week + date format")
  }

  test("Relaxed date: jan 1st should FAIL (lowercase)") {
    val result = dateParsers.parseAll(dateParsers.monthDay, "jan 1st")
    // Note: The parser might be case-sensitive
    assert(!result.successful || result.get.toString.contains("jan"), 
      "Parser might be case-sensitive")
  }

  test("Relaxed date: february twenty-eighth should FAIL (cardinal)") {
    val result = dateParsers.parseAll(dateParsers.monthDay, "february twenty-eighth")
    assert(!result.successful, "Parser should not succeed with cardinal number day")
  }

  test("Relaxed date: December 3rd should WORK") {
    val result = dateParsers.parseAll(dateParsers.monthDay, "december 3rd")
    assert(result.successful, "Parser should succeed with month name + ordinal")
  }

  test("Relaxed date: fourth of July should WORK") {
    val result = dateParsers.parseAll(dateParsers.monthDay, "fourth of july")
    assert(result.successful, "Parser should succeed with ordinal + of + month")
  }

  // ============================================================================
  // DATE ALTERNATIVES - Currently not supported for dates
  // ============================================================================

  test("Date alternative: next wed or thurs should FAIL") {
    val result = dateParsers.parseAll(dateParsers.adjuster, "next wed or thurs")
    assert(!result.successful, "Parser should not succeed with day alternatives")
  }

  test("Date alternative: oct 3rd or 4th should FAIL") {
    val result = dateParsers.parseAll(dateParsers.monthDay, "oct 3rd or 4th")
    assert(!result.successful, "Parser should not succeed with date alternatives")
  }

  // ============================================================================
  // PREFIXES - Should work (for verification)
  // ============================================================================

  test("Prefix: day after should WORK") {
    val result = dateParsers.parseAll(dateParsers.adjuster, "day after")
    assert(result.successful, "Parser should succeed with 'day after'")
  }

  test("Prefix: the day before should WORK") {
    val result = dateParsers.parseAll(dateParsers.adjuster, "the day before")
    assert(result.successful, "Parser should succeed with 'the day before'")
  }

  test("Prefix: the monday after should WORK") {
    val result = dateParsers.parseAll(dateParsers.adjuster, "the monday after")
    assert(result.successful, "Parser should succeed with 'the monday after'")
  }

  test("Prefix: the monday before should WORK") {
    val result = dateParsers.parseAll(dateParsers.adjuster, "the monday before")
    assert(result.successful, "Parser should succeed with 'the monday before'")
  }

  test("Prefix: 2 fridays before should WORK") {
    val result = dateParsers.parseAll(dateParsers.adjuster, "2 fridays before")
    assert(result.successful, "Parser should succeed with '2 fridays before'")
  }

  test("Prefix: 4 tuesdays after should WORK") {
    val result = dateParsers.parseAll(dateParsers.adjuster, "4 tuesdays after")
    assert(result.successful, "Parser should succeed with '4 tuesdays after'")
  }

  // ============================================================================
  // RELATIVE DATES - Should work (for verification)
  // ============================================================================

  test("Relative: next thursday should WORK") {
    val result = dateParsers.parseAll(dateParsers.adjuster, "next thursday")
    assert(result.successful, "Parser should succeed with 'next thursday'")
  }

  test("Relative: last wednesday should WORK") {
    val result = dateParsers.parseAll(dateParsers.adjuster, "last wednesday")
    assert(result.successful, "Parser should succeed with 'last wednesday'")
  }

  test("Relative: today should WORK") {
    val result = dateParsers.parseAll(dateParsers.adjuster, "today")
    assert(result.successful, "Parser should succeed with 'today'")
  }

  test("Relative: tomorrow should WORK") {
    val result = dateParsers.parseAll(dateParsers.adjuster, "tomorrow")
    assert(result.successful, "Parser should succeed with 'tomorrow'")
  }

  test("Relative: yesterday should WORK") {
    val result = dateParsers.parseAll(dateParsers.adjuster, "yesterday")
    assert(result.successful, "Parser should succeed with 'yesterday'")
  }

  test("Relative: next week should WORK") {
    val result = dateParsers.parseAll(dateParsers.adjuster, "next week")
    assert(result.successful, "Parser should succeed with 'next week'")
  }

  test("Relative: next month should WORK") {
    val result = dateParsers.parseAll(dateParsers.adjuster, "next month")
    assert(result.successful, "Parser should succeed with 'next month'")
  }

  test("Relative: next year should WORK") {
    val result = dateParsers.parseAll(dateParsers.adjuster, "next year")
    assert(result.successful, "Parser should succeed with 'next year'")
  }

  test("Relative: 3 days from should WORK (without now)") {
    val result = dateParsers.parseAll(dateParsers.adjuster, "3 days from")
    assert(result.successful, "Parser should succeed with '3 days from'")
  }

  test("Relative: 3 days from now should FAIL") {
    val result = dateParsers.parseAll(dateParsers.adjuster, "3 days from now")
    assert(!result.successful, "Parser should not succeed with '3 days from now' (now is not consumed)")
  }

  test("Relative: three weeks ago should WORK") {
    val result = dateParsers.parseAll(dateParsers.adjuster, "three weeks ago")
    // This should work: period="three weeks", direction="ago"
    assert(result.successful, "Parser should succeed with 'three weeks ago'")
  }
}
