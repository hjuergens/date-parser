package de.juergens

import java.time.temporal._
import java.time.{DayOfWeek, LocalDate}

import de.juergens.util.{Direction, Down, Ordinal, Up}

package object time {
//  implicit class TemporalQueryImpl(accessor: (TemporalAccessor) => Boolean) extends TemporalQuery[LocalDate] {
//    override def queryFrom(temporal: TemporalAccessor): LocalDate = accessor.queryFrom(temporal)
//  }

  implicit class TemporalAdjusterImpl(adjuster: (Temporal) => Temporal) extends TemporalAdjuster {
    override def adjustInto(temporal: Temporal): Temporal = adjuster(temporal)
  }

  object TemporalSeek {
    def apply(ordinal: Ordinal, ta: TemporalAccessor, direction: Direction) = ta match {
      case dayOfWeek: DayOfWeek => DayOfWeekAdjuster(ordinal, dayOfWeek, direction)
      case month: java.time.Month => MonthAdjuster(ordinal, month, direction)
    }
  }

  object TemporalPeriodSeek {
    import Ordinal._
    def apply(ordinal: Ordinal, ta: TemporalUnit, direction: Direction) : TemporalAdjuster =
      (temporal: Temporal) => ta.addTo(temporal, direction * ordinal)
//      ta match {
//        case ChronoUnit.WEEKS => // TODO ISO-8601 standard considers Monday
//          direction match {
//            case Up => DayOfWeekAdjuster(ordinal, DayOfWeek.SUNDAY, direction)
//            case Down => DayOfWeekAdjuster(ordinal, DayOfWeek.SATURDAY, direction)
//          }
//        case ChronoUnit.MONTHS =>
//          direction match {
//            case Up => (temporal: Temporal) => ChronoUnit.MONTHS.addTo(temporal, direction * ordinal)
//            case Down => DayOfMonthAdjuster(ordinal, java.time.Month.DECEMBER, direction)
//          }
//        case ChronoUnit.YEARS =>
//          direction match {
//            case Up => MonthAdjuster(ordinal, java.time.Month.JANUARY, direction)
//            case Down => MonthAdjuster(ordinal, java.time.Month.DECEMBER, direction)
//          }
//      }
  }
}