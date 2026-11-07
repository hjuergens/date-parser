package de.juergens.time

import java.time.LocalDate

import org.junit.Assert._
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(value = classOf[JUnit4])
class QuarterAdjusterTest {

  private def adjust(steps: Int, date: LocalDate): LocalDate =
    LocalDate.from(QuarterAdjuster(steps).adjustInto(date))

  @Test
  def keepsPositionInQuarter(): Unit = {
    assertEquals(LocalDate.of(2016, 5, 15), adjust(1, LocalDate.of(2016, 2, 15)))
    assertEquals(LocalDate.of(2016, 8, 15), adjust(2, LocalDate.of(2016, 2, 15)))
    assertEquals(LocalDate.of(2016, 2, 15), adjust(0, LocalDate.of(2016, 2, 15)))
  }

  @Test
  def wrapsAroundWithinTheYear(): Unit = {
    // used to fail with Quarter.of(0)
    assertEquals(LocalDate.of(2016, 2, 15), adjust(1, LocalDate.of(2016, 11, 15)))
    assertEquals(LocalDate.of(2016, 11, 15), adjust(-1, LocalDate.of(2016, 2, 15)))
    assertEquals(LocalDate.of(2016, 11, 15), adjust(7, LocalDate.of(2016, 2, 15)))
  }
}
