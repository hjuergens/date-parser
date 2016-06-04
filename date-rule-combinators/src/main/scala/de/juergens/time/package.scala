package de.juergens

import java.time.temporal._
import java.time.{DayOfWeek, LocalDate}

import de.juergens.util.{Direction, Down, Ordinal, Up}

import scala.language.implicitConversions

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
        case ChronoUnit.DAYS =>
          implicit val description = s"$ordinal Day"
          direction match {
            case Up => {
              (date:LocalDate) => date.plus(ordinal, ChronoUnit.DAYS)
            }
            case Down => (date:LocalDate)=> date.minus(ordinal, ChronoUnit.DAYS)
          }
        case ChronoUnit.WEEKS => // TODO ISO-8601 standard considers Monday
          implicit val description = s"$ordinal Week"
          direction match {
            case Up => (date:LocalDate) => {
              date.
                plus(ordinal, ChronoUnit.WEEKS).
                plus(1, ChronoUnit.DAYS).
                `with`(DayOfWeek.MONDAY). // TemporalAdjusters.firstDayOfMonth())
                minus(1, ChronoUnit.DAYS)
            }

            case Down => (date:LocalDate) => {
              date.
                minus(ordinal, ChronoUnit.WEEKS).
                plus(1, ChronoUnit.DAYS).
                `with`(DayOfWeek.SUNDAY).
                minus(1, ChronoUnit.DAYS)// TemporalAdjusters.firstDayOfMonth())
            }

          }
        case ChronoUnit.MONTHS => {
          implicit val description = s"$ordinal Month"
          direction match {
            case Up => (date:LocalDate) => {
              date.
                plus(ordinal, ChronoUnit.MONTHS).
                `with`(TemporalAdjusters.firstDayOfMonth())
            }

            case Down => (date:LocalDate) => {
              date.
                minus(ordinal, ChronoUnit.MONTHS).
                `with`(TemporalAdjusters.lastDayOfMonth())
            }

          }
        }
        case ChronoUnit.YEARS =>
          implicit val description = s"$ordinal Year"
          direction match {
            case Up => (date:LocalDate) => {
              date.
                  plus(ordinal, ChronoUnit.YEARS).
                  `with`(TemporalAdjusters.firstDayOfYear())

              }

            case Down => (date:LocalDate) => {
              date.
                  minus(ordinal, ChronoUnit.YEARS).
                  `with`(TemporalAdjusters.lastDayOfYear())
              }

          }
        case IsoFields.QUARTER_YEARS => // TODO IsoFields.QUARTER_YEARS
          implicit val description = s"$ordinal Quarter"
          direction match {
            case Up =>
              (localDate:LocalDate)=> localDate.
                plus(ordinal, ChronoUnit.YEARS).
                `with`(java.time.Month.JANUARY).
                `with`(TemporalAdjusters.firstDayOfMonth())

            case Down =>
              (localDate:LocalDate)=> localDate.
                minus(ordinal, ChronoUnit.YEARS).
                `with`(java.time.Month.DECEMBER).
                `with`(TemporalAdjusters.lastDayOfMonth())

          }
      }

    override def toString = s"TemporalPeriodSeek()"
  }
}