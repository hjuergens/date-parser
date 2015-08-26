package de.juergens

import java.time.{LocalDate, DayOfWeek}
import java.time.temporal._

import de.juergens.util.{Direction, Down, Ordinal, Up}

package object time {
  implicit class TemporalAdjusterWrapper(adjuster: (Temporal) => Temporal) extends TemporalAdjuster {
    override def adjustInto(temporal: Temporal): Temporal = adjuster(temporal)
  }

  object TemporalSeek {
    import java.time.Month
    def apply(ordinal: Ordinal, ta: TemporalAccessor, direction: Direction) = ta match {
      case dayOfWeek: DayOfWeek => DayOfWeekAdjuster(ordinal, dayOfWeek, direction)
      case month: Month => MonthAdjuster(ordinal, month, direction)
    }
  }

  object TemporalPeriodSeek {
    import Ordinal._
    def apply(ordinal: Ordinal, ta: TemporalUnit) : LocalDateAdjuster = {
      val direction =  Direction(ordinal.toInt)
      apply(ordinal.abs, ta, direction)
    }
    def apply(ordinal: Ordinal, ta: TemporalUnit, direction: Direction): LocalDateAdjuster =
      ta match {
        case ChronoUnit.WEEKS => // TODO ISO-8601 standard considers Monday
          direction match {
            case Up => new TemporalAdjuster with LocalDateAdjuster {
              override def adjustInto(temporal: Temporal): Temporal = {
                temporal.
                  plus(ordinal, ChronoUnit.WEEKS).
                  plus(1, ChronoUnit.DAYS).
                  `with`(DayOfWeek.MONDAY). // TemporalAdjusters.firstDayOfMonth())
                  minus(1, ChronoUnit.DAYS)
              }
            } // DayOfWeekAdjuster(ordinal, DayOfWeek.SUNDAY, direction)
            case Down => new TemporalAdjuster with LocalDateAdjuster {
              override def adjustInto(temporal: Temporal): Temporal = {
                temporal.
                  minus(ordinal, ChronoUnit.WEEKS).
                  plus(1, ChronoUnit.DAYS).
                  `with`(DayOfWeek.SUNDAY).
                  minus(1, ChronoUnit.DAYS)// TemporalAdjusters.firstDayOfMonth())
              }
            } // DayOfWeekAdjuster(ordinal, DayOfWeek.SATURDAY, direction)
          }
        case ChronoUnit.MONTHS => {
          direction match {
            case Up => new TemporalAdjuster with LocalDateAdjuster {
              override def adjustInto(temporal: Temporal): Temporal = {
                temporal.
                  plus(ordinal, ChronoUnit.MONTHS).
                  `with`(TemporalAdjusters.firstDayOfMonth())
              }
            }
            case Down => new TemporalAdjuster with LocalDateAdjuster {
              override def adjustInto(temporal: Temporal): Temporal = {
                temporal.
                  minus(ordinal, ChronoUnit.MONTHS).
                  `with`(TemporalAdjusters.lastDayOfMonth())
              }
            }
          }
        }
        case ChronoUnit.YEARS =>
          direction match {
            case Up => new TemporalAdjuster with LocalDateAdjuster {
              override def adjustInto(temporal: Temporal): Temporal = {
                temporal.
                  plus(ordinal, ChronoUnit.YEARS).
                  `with`(java.time.Month.JANUARY).
                  `with`(TemporalAdjusters.firstDayOfMonth())
              }
            } // MonthAdjuster(ordinal, java.time.Month.JANUARY, direction)
            case Down => new TemporalAdjuster with LocalDateAdjuster {
              override def adjustInto(temporal: Temporal): Temporal = {
                temporal.
                  minus(ordinal, ChronoUnit.YEARS).
                  `with`(java.time.Month.DECEMBER).
                  `with`(TemporalAdjusters.lastDayOfMonth())
              }
            } // MonthAdjuster(ordinal, java.time.Month.DECEMBER, direction)
          }
        case IsoFields.QUARTER_YEARS =>
          direction match {
            case Up => TemporalAdjusters.ofDateAdjuster(
              (localDate:LocalDate)=> localDate.
                  plus(ordinal, ChronoUnit.YEARS).
                  `with`(java.time.Month.JANUARY).
                  `with`(TemporalAdjusters.firstDayOfMonth())
              )
            case Down => TemporalAdjusters.ofDateAdjuster(
              (localDate:LocalDate)=> localDate.
                  minus(ordinal, ChronoUnit.YEARS).
                  `with`(java.time.Month.DECEMBER).
                  `with`(TemporalAdjusters.lastDayOfMonth())
            )
          }
      }
  }
}