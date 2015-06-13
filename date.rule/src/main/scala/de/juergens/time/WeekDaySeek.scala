package de.juergens.time

import java.time.temporal._
import java.time.{DayOfWeek, LocalDate}

import de.juergens.util._
import scala.language.implicitConversions

/**
 * Created by juergens on 27.05.15.
 */
case class WeekDaySeek(ordinal: Ordinal, dayOfWeek: DayOfWeek, direction: Direction) extends TemporalAdjuster {

  val adjuster: TemporalAdjuster = TemporalAdjusters.next(dayOfWeek)
  private val Ordinal(number) = ordinal

  override def adjustInto(temporal: Temporal): Temporal = {
    require(temporal.isSupported(ChronoField.DAY_OF_WEEK), s"$temporal has to support day-of-week")
    temporal.ensuring( _.isSupported(ChronoField.DAY_OF_WEEK))

    import TemporalAdjusters.{next, previous}

    val adjuster: TemporalAdjuster = direction match {
      case Up => next(dayOfWeek)
      case Down => previous(dayOfWeek)
    }

    var date = temporal

    if (number > 0) {
      date = adjuster.adjustInto(temporal)
    }
    if (date == temporal && number > 0) {
      date = adjuster.adjustInto(temporal)
    }
    if (number > 1) {
      date = (1 to (number - 1)).foldLeft[Temporal](date) { (x: Temporal, _) => adjuster.adjustInto(x) }
    }

    assert(temporal.isInstanceOf[LocalDate])

    temporal.get(ChronoField.DAY_OF_WEEK) == dayOfWeek.getValue

    date
  }
}

import scala.language.implicitConversions
object TemporalQueryImplicits {

  implicit class TemporalQueryImpl(accessor: (TemporalAccessor) => Boolean) extends TemporalQuery[LocalDate] {
    override def queryFrom(temporal: TemporalAccessor): LocalDate = accessor.queryFrom(temporal)
  }

}

case class MonthSeek(ordinal: Ordinal, month: java.time.Month, direction: Direction) extends TemporalAdjuster {

  private def signum = direction
  private val Ordinal(number) = ordinal

  override def adjustInto(temporal: Temporal): Temporal = {
    var date: LocalDate = LocalDate.from(temporal)

    require(temporal.isSupported(ChronoField.MONTH_OF_YEAR), s"$temporal has to support month-of-year")
    temporal.ensuring(_.isSupported(ChronoField.MONTH_OF_YEAR))

    val currentMonth = date.get(ChronoField.MONTH_OF_YEAR)

    val isBeforeMonth = (accessor: TemporalAccessor) => accessor.get(ChronoField.MONTH_OF_YEAR) < month.getValue
    val isAfterMonth = (accessor: TemporalAccessor) => accessor.get(ChronoField.MONTH_OF_YEAR) > month.getValue
    val isMonth = (accessor: TemporalAccessor) => accessor.get(ChronoField.MONTH_OF_YEAR) == month.getValue

    if (number > 0)
      date = date.`with`(ChronoField.MONTH_OF_YEAR, month.getValue)

    var rest: Int = {
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

    //    assert( temporal.query(isMonth) )

    temporal.get(ChronoField.MONTH_OF_YEAR) == month.getValue

    date
  }
}
