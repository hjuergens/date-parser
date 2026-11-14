package de.juergens.time

import java.time._
import java.time.temporal.ChronoField._
import java.time.temporal._

import de.juergens.util._

import scala.language.implicitConversions



case class DayOfWeekAdjuster(ordinal: Ordinal, dayOfWeek: DayOfWeek, direction: Direction)
  extends TemporalAdjuster
  with LocalDateAdjuster {

  val adjuster: TemporalAdjuster = TemporalAdjusters.next(dayOfWeek)
  private val Ordinal(number) = ordinal

  override def adjustInto(temporal: Temporal): Temporal = {
    //    require(temporal.isSupported(ChronoField.DAY_OF_WEEK), s"$temporal has to support day-of-week")
    //    temporal.ensuring( _.isSupported(ChronoField.DAY_OF_WEEK))

    import TemporalAdjusters.{next, previous}

    val adjuster: TemporalAdjuster = direction match {
      case Up => next(dayOfWeek)
      case Down => previous(dayOfWeek)
    }

    var date : Temporal =
      if (temporal.isSupported(EPOCH_DAY)) {
        LocalDate.from(temporal)
      } else {
        YearMonth.from(temporal).atDay(1)
      }

    if (number > 0) {
      date = adjuster.adjustInto(date)
    }
    if (date == temporal && number > 0) {
      date = adjuster.adjustInto(date)
    }
    if (number > 1) {
      date = (1 to (number - 1)).foldLeft[Temporal](date) { (x: Temporal, _) => adjuster.adjustInto(x) }
    }

//    assert(temporal.isInstanceOf[LocalDate])

    val time: LocalTime = temporal.query(TemporalQueries.localTime)
    if (time != null)
      date.`with`(time)
    else date
  }
}

