/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.juergens.time

import java.time.temporal._
import de.juergens.time.{Date => _ }
import java.time.{Duration, LocalDate => Date}

import de.juergens.time.{Date => _}

/**
 *
 * @author juergens
 */
abstract sealed class TimeUnit {
  protected val _chronoUnit: ChronoUnit

  override final def toString: String = this match {
    case TimeUnit(name) => name
  }

  final def isDurationEstimated: Boolean = _chronoUnit.isDurationEstimated

  final def isSupportedBy(temporal: Temporal): Boolean = _chronoUnit.isSupportedBy(temporal)

  //  final override def toString: String = _chronoUnit.toString
  final def values(): Array[ChronoUnit] = ChronoUnit.values()

  final def isDateBased: Boolean = _chronoUnit.isDateBased

  final def between(temporal1Inclusive: Temporal, temporal2Exclusive: Temporal): Long = _chronoUnit.between(temporal1Inclusive, temporal2Exclusive)

  final def valueOf(name: String): ChronoUnit = ChronoUnit.valueOf(name)

  final def isTimeBased: Boolean = _chronoUnit.isTimeBased

  final def getDuration: Duration = _chronoUnit.getDuration

  final def addTo[R <: Temporal](temporal: R, amount: Long): R = _chronoUnit.addTo(temporal, amount)
}

case object DayUnit extends TimeUnit {
  override val _chronoUnit = ChronoUnit.DAYS
  //  def apply(offset: Int)(date: Date) = {
  //    val number = DateComponent.toNumber(date) + offset
  //    DateComponent.fromNumber(number)
  //  }
}

case object WeekUnit extends TimeUnit {
  override val _chronoUnit = ChronoUnit.WEEKS
}

case object MonthUnit extends TimeUnit {
  override val _chronoUnit = ChronoUnit.MONTHS
}

case object YearUnit extends TimeUnit {
  override val _chronoUnit = ChronoUnit.YEARS
}

object TimeUnit {
  def apply(str: String): TimeUnit = str match {
    case "day" | "days" | "day(s)" => DayUnit
    case "week" | "weeks" | "week(s)" => WeekUnit
    case "month" | "months" | "month(s)" => MonthUnit
    case "year" | "years" | "year(s)" => YearUnit
  }

  def unapply(unit: TimeUnit): Option[String] = PartialFunction.condOpt(unit) {
    case DayUnit => "day(s)"
    case WeekUnit => "week(s)"
    case MonthUnit => "month(s)"
    case YearUnit => "year(s)"
  }
}
