/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.juergens.time

/**
 *
 * @author juergens
 */
abstract sealed class TimeUnit extends DateComponent {
  override final def toString: String = this match {
    case TimeUnit(name) => name
  }
}

case object DayUnit extends TimeUnit {
  def apply(offset: Int)(date: Date) = {
    val number = DateComponent.toNumber(date) + offset
    DateComponent.fromNumber(number)
  }
}

case object WeekUnit extends TimeUnit

case object MonthUnit extends TimeUnit

case object QuarterUnit extends TimeUnit

case object YearUnit extends TimeUnit

object TimeUnit {
  def apply(str: String): TimeUnit = str match {
    case "day"     => DayUnit
    case "week"    => WeekUnit
    case "month"   => MonthUnit
    case "quarter" => QuarterUnit
    case "year"    => YearUnit
  }

  def unapply(unit: TimeUnit): Option[String] = PartialFunction.condOpt(unit) {
    case DayUnit     => "day"
    case WeekUnit    => "week"
    case MonthUnit   => "month"
    case QuarterUnit => "quarter"
    case YearUnit    => "year"
  }
}
