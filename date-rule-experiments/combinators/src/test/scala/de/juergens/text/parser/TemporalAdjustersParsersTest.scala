package de.juergens.text.parser

import java.time.LocalDate

import de.juergens.text.{DateRuleParsers, TemporalAdjustersParsers}
import org.junit.Assert._
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(value = classOf[JUnit4])
class TemporalAdjustersParsersTest {

  private val parsers = new DateRuleParsers with TemporalAdjustersParsers

  // a Wednesday
  private val reference = LocalDate.of(2016, 6, 15)

  private def adjust(parser: parsers.Parser[_ <: java.time.temporal.TemporalAdjuster], text: String): LocalDate = {
    val result = parsers.parseAll(parser, text)
    assertTrue(result.toString, result.successful)
    reference.`with`(result.get)
  }

  @Test
  def monthAndYearBoundaries(): Unit = {
    assertEquals(LocalDate.of(2016, 6, 1), adjust(parsers.firstDayOfMonth, "first day of month"))
    assertEquals(LocalDate.of(2016, 6, 30), adjust(parsers.lastDayOfMonth, "last day of month"))
    assertEquals(LocalDate.of(2016, 7, 1), adjust(parsers.firstDayOfNextMonth, "first day of next month"))
    assertEquals(LocalDate.of(2016, 1, 1), adjust(parsers.firstDayOfYear, "first day of year"))
    assertEquals(LocalDate.of(2016, 12, 31), adjust(parsers.lastDayOfYear, "last day of year"))
    assertEquals(LocalDate.of(2017, 1, 1), adjust(parsers.firstDayOfNextYear, "first day of next year"))
  }

  @Test
  def dayOfWeekInMonth(): Unit = {
    assertEquals(LocalDate.of(2016, 6, 6), adjust(parsers.firstInMonth, "first monday in month"))
    // used to return the first instead of the last monday
    assertEquals(LocalDate.of(2016, 6, 27), adjust(parsers.lastInMonth, "last monday in month"))
  }

  @Test
  def relativeDayOfWeek(): Unit = {
    assertEquals(LocalDate.of(2016, 6, 22), adjust(parsers.nextDayOfWeek, "next wednesday"))
    assertEquals(LocalDate.of(2016, 6, 15), adjust(parsers.nextOrSameDayOfWeek, "next or same wednesday"))
    assertEquals(LocalDate.of(2016, 6, 8), adjust(parsers.previousDayOfWeek, "previous wednesday"))
    assertEquals(LocalDate.of(2016, 6, 15), adjust(parsers.previousOrSameDayOfWeek, "previous or same wednesday"))
    assertEquals(LocalDate.of(2016, 6, 17), adjust(parsers.nextDayOfWeek, "next friday"))
  }

  @Test
  def rejectsUnknownPhrase(): Unit = {
    assertFalse(parsers.parseAll(parsers.firstDayOfMonth, "first day of week").successful)
    assertFalse(parsers.parseAll(parsers.nextDayOfWeek, "next holiday").successful)
  }
}
