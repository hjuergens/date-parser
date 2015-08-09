package de.juergens.time

import java.time.temporal._

import de.juergens.util.{Down, Up, Direction, Ordinal}

case class MonthAdjuster(ordinal: Ordinal, month: java.time.Month, direction: Direction) extends TemporalAdjuster {

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

