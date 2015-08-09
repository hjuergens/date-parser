package de.juergens.time

import java.time.LocalDate
import java.time.temporal.{TemporalUnit, ChronoUnit}
import java.time.{Period => JPeriod}

@deprecated("use java.time instead", "0.0.2")
case class Period(count: Int, unit: TimeUnit)

object Period {
  val Infinity: Period = new Period(Int.MaxValue, null)

  def of(count: Int, unit: TemporalUnit) = unit match {
    case ChronoUnit.DAYS =>  JPeriod.ofDays(count)
    case ChronoUnit.WEEKS => JPeriod.ofWeeks(count)
    case ChronoUnit.MONTHS =>JPeriod.ofMonths(count)
    case ChronoUnit.YEARS => JPeriod.ofYears(count)
  }
}

@deprecated("use java.time instead", "0.0.2")
trait Calendar {
  def advance(date: LocalDate, period: Period): LocalDate
}

@deprecated("use java.time instead", "0.0.2")
object Calendar {
  def calendarForward: Calendar = new Calendar {
    def advance(date: LocalDate, period: Period): LocalDate = ???
  }
}
