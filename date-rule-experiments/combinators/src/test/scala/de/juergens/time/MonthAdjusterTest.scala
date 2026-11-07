package de.juergens.time

import java.time.{LocalDate, Month, Year}

import de.juergens.util.{Ordinal, Up}
import org.junit.Assert._
import org.junit.Test

class MonthAdjusterTest {
  @Test
  @throws[Exception]
  def adjustInto: Unit = {
    val adjuster = MonthAdjuster(Ordinal(1),Month.JULY,Up)
    val expected = LocalDate.parse("1776-07-31")
    val actual = adjuster.adjustInto(Year.of(1775).atMonth(Month.DECEMBER).atDay(4))
    assertEquals( expected, actual )

  }
}