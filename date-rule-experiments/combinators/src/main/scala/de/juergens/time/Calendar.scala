package de.juergens.time

import java.time.temporal._
import java.time.{Duration, LocalDate, Period}
import java.util.function.UnaryOperator

object PeriodDuration {
  @deprecated("use java.time direct instead", "0.0.4")
  def of(count: Int, unit: TemporalUnit) : TemporalAmount = unit match {
    case ChronoUnit.DAYS =>  Period.ofDays(count)
    case ChronoUnit.WEEKS => Period.ofWeeks(count)
    case ChronoUnit.MONTHS =>Period.ofMonths(count)
    case ChronoUnit.YEARS => Period.ofYears(count)
    case ChronoUnit.HOURS=> Duration.ofHours(count)
    case ChronoUnit.MINUTES=> Duration.ofMinutes(count)
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
