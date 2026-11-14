package de.juergens.time

import java.time.temporal.ChronoUnit
import java.time.{Duration, Period}

import org.junit.Assert._
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(value = classOf[JUnit4])
class TimeUnitTest {

  @Test
  def applyAcceptsSingularPluralAndOptionalS(): Unit = {
    for (word <- Seq("day", "days", "day(s)")) assertEquals(ChronoUnit.DAYS, TimeUnit(word))
    for (word <- Seq("week", "weeks", "week(s)")) assertEquals(ChronoUnit.WEEKS, TimeUnit(word))
    for (word <- Seq("month", "months", "month(s)")) assertEquals(ChronoUnit.MONTHS, TimeUnit(word))
    for (word <- Seq("year", "years", "year(s)")) assertEquals(ChronoUnit.YEARS, TimeUnit(word))
  }

  @Test
  def unapplyRoundTrips(): Unit = {
    for (unit <- Seq(ChronoUnit.DAYS, ChronoUnit.WEEKS, ChronoUnit.MONTHS, ChronoUnit.YEARS)) {
      val TimeUnit(name) = unit
      assertEquals(unit, TimeUnit(name))
    }
    assertEquals(None, TimeUnit.unapply(ChronoUnit.HOURS))
  }

  @Test(expected = classOf[MatchError])
  def unknownUnit(): Unit = TimeUnit("fortnight")

  @Test
  @deprecated("tests deprecated PeriodDuration", "0.0.4")
  def periodDurationOf(): Unit = {
    assertEquals(Period.ofDays(2), PeriodDuration.of(2, ChronoUnit.DAYS))
    assertEquals(Period.ofWeeks(2), PeriodDuration.of(2, ChronoUnit.WEEKS))
    assertEquals(Period.ofMonths(2), PeriodDuration.of(2, ChronoUnit.MONTHS))
    assertEquals(Period.ofYears(2), PeriodDuration.of(2, ChronoUnit.YEARS))
    assertEquals(Duration.ofHours(2), PeriodDuration.of(2, ChronoUnit.HOURS))
    assertEquals(Duration.ofMinutes(2), PeriodDuration.of(2, ChronoUnit.MINUTES))
  }
}
