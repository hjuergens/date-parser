package de.juergens.time

import java.time.temporal._
import java.time.{DayOfWeek, LocalDate}

import de.juergens.util._
import scala.language.implicitConversions



case class DayOfWeekAdjuster(ordinal: Ordinal, dayOfWeek: DayOfWeek, direction: Direction) extends TemporalAdjuster {

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
//    assert( temporal.get(ChronoField.DAY_OF_WEEK) == dayOfWeek.get(ChronoField.DAY_OF_WEEK) )

    date
  }

  final def apply(t: Temporal): Temporal = adjustInto(t)
}

