package de.juergens.time

import java.time.LocalDate
import java.time.temporal._
import java.time.{Period => JPeriod}
import java.util.function.UnaryOperator

object Period {
  def of(count: Int, unit: TemporalUnit) = unit match {
    case ChronoUnit.DAYS =>  JPeriod.ofDays(count)
    case ChronoUnit.WEEKS => JPeriod.ofWeeks(count)
    case ChronoUnit.MONTHS =>JPeriod.ofMonths(count)
    case ChronoUnit.YEARS => JPeriod.ofYears(count)
  }
}

trait Calendar {

  protected def dateBasedAdjuster(amount: TemporalAmount): UnaryOperator[LocalDate] =
    new UnaryOperator[LocalDate] {
      override def apply(t: LocalDate): LocalDate = advance(t, amount)
    }

  def adjuster(amount:TemporalAmount) : TemporalAdjuster =
    TemporalAdjusters.ofDateAdjuster(dateBasedAdjuster(amount))

  def advance(temporal: LocalDate, amount: TemporalAmount): LocalDate
}

object Calendar {
  val nullCalendar: Calendar = new Calendar {
    def advance(date: LocalDate, period: TemporalAmount): LocalDate = {
      date.plus(period)
    }
  }
}
