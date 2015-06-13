package de.juergens.time

import java.time.LocalDate

@deprecated("use java.time instead", "0.0.2")
case class Period(count: Int, unit: TimeUnit)

object Period {
  val Infinity: Period = new Period(Int.MaxValue, null)
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
