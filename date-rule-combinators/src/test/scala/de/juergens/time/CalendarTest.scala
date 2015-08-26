package de.juergens.time

import java.time.LocalDate
import java.time.temporal.ChronoUnit

import org.junit.runner.RunWith
import org.specs2.mutable.Specification

@RunWith(classOf[org.specs2.runner.JUnitRunner])
class CalendarTest extends Specification {

  override def is = sequential ^ s2"""

  We can
    create a null-calendar                                      $create
    an determinate the date according to a period of three days $advance
                                                     """

  var cal : Calendar = _

  def create = {
    cal = Calendar.nullCalendar
    cal must_== Calendar.nullCalendar
  }

  def advance = {
    val d = cal.advance(LocalDate.of(2015,8,18), Period.of(3, ChronoUnit.DAYS))
    d should_==(LocalDate.of(2015,8,21))
  }

}