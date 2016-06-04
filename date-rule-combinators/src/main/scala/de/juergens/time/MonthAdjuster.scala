package de.juergens.time

import java.time.Month
import java.time.temporal._

import de.juergens.util.{Direction, Down, Ordinal, Up}
import org.threeten.extra.Quarter

case class MonthAdjuster(ordinal: Ordinal, month: Month, direction: Direction)
  extends LocalDateAdjuster {

  private def signum = direction
  private val Ordinal(number) = ordinal

  val isBeforeMonth = (accessor: TemporalAccessor) => accessor.get(ChronoField.MONTH_OF_YEAR) < month.getValue
  val isAfterMonth = (accessor: TemporalAccessor) => accessor.get(ChronoField.MONTH_OF_YEAR) > month.getValue
  val isMonth = (accessor: TemporalAccessor) => accessor.get(ChronoField.MONTH_OF_YEAR) == month.getValue

  override def adjustInto(temporal: Temporal): Temporal = {
    var date = temporal

    require(temporal.isSupported(ChronoField.MONTH_OF_YEAR), s"$temporal has to support month-of-year")
    temporal.ensuring(_.isSupported(ChronoField.MONTH_OF_YEAR))

    if (number > 0)
      date = date.`with`(ChronoField.MONTH_OF_YEAR, month.getValue)

    val rest: Int = {
      if (isMonth(date)) {
        signum * number
      } else {
        if (direction == Up && isBeforeMonth(date)) signum * number - 1
        else if (direction == Down && isAfterMonth(date)) signum * number + 1
        else signum * number
      }
    }

    val currentYear = date.get(ChronoField.YEAR)
    date = date.`with`(ChronoField.YEAR, currentYear + rest)

    direction match {
      case Up => date = TemporalAdjusters.lastDayOfMonth().adjustInto(date)
      case Down => date = TemporalAdjusters.firstDayOfMonth().adjustInto(date)
    }

    assert( temporal.get(ChronoField.MONTH_OF_YEAR) == month.getValue )

    date
  }
}

case class QuarterAdjuster(steps:Int)
  extends LocalDateAdjuster {
  override def adjustInto(temporal: Temporal): Temporal = {
    require(temporal.isSupported(ChronoField.MONTH_OF_YEAR), s"$temporal has to support month-of-year")
    temporal.ensuring(_.isSupported(ChronoField.MONTH_OF_YEAR))

    val quarterOfYear = (Quarter.from(temporal).getValue + steps) % 4
    assert(0<= quarterOfYear && quarterOfYear <=4)
    temporal.`with`(Quarter.of(quarterOfYear))
  }
}
