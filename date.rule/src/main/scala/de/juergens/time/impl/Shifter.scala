/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.juergens.time.impl


import java.time.temporal.{ChronoUnit, Temporal}
import java.time.{LocalDate => Date}

import de.juergens.time.Date.EnrichedLocalDate
import de.juergens.time._
import de.juergens.util.{Direction, Up}

import scala.language.implicitConversions

/**
 * @param predicate The Condition the DateShifter tries to meet by increasing or decreasing input dates.
 */
case class DateShifter(predicate: (Temporal) => Boolean, direction:Direction = Up) extends Shifter/*extends Serializable*/ {

  /**
   * helper method for moving dates until a condition is true
   */
  private def moveDateUntil(anOffset: Int)(aDate: Date): Date = {
    var date = aDate
    while (!predicate(date))
      date += anOffset
    date
  }

  /**
   * move date forward until condition is met
   */
  def inc(date: Date): Date = {
    moveDateUntil(1)(date)
  }

  /**
   * move date backward until condition is met
   */
  def dec(date: Date): Date = {
    moveDateUntil(-1)(date)
  }

  /**
   * skip forward to next date that meets the condition
   * @param minOffset allows optimization by skipping several days at once (used for weekday shifters)
   */
  def next(date: Date, minOffset: Int = 1): Date = {
    if (predicate(date))
      inc(date + minOffset)
    else
      inc(date)
  }

  /**
   * skip backward to previous date that meets the condition
   */
  def previous(date: Date, minOffset: Int = 1): Date = {
    if (predicate(date))
      dec(date - minOffset)
    else
      dec(date)
  }

  override def shift(t: Date): Date = moveDateUntil(direction * 1)(t)

  override def adjustInto(temporal: Temporal): Temporal = {
    var l = temporal
    while(!predicate(l)) l = l.plus(1,ChronoUnit.DAYS)
    l
  }
}

abstract class TimeUnitShifter extends Shifter {
  val timeUnit : TimeUnit
  def step : Int
  override val direction = Direction(step)

  final def shift(t: Date) = java.time.LocalDate.from(adjustInto(t))
}

case class WeekShifter(step: Int) extends TimeUnitShifter {
  val timeUnit = WeekUnit
  
  override def toString = "%+d".format(step) + " " + WeekUnit.toString

  override def adjustInto(temporal: Temporal): Temporal = temporal.plus(step, ChronoUnit.WEEKS)
}

case class DayShifter(step: Int) extends TimeUnitShifter {
  val timeUnit = DayUnit 
  
  override def toString = "%+d".format(step) + " " + DayUnit.toString

  override def adjustInto(temporal: Temporal): Temporal = temporal.plus(step, ChronoUnit.DAYS)
}

case class YearShifter(step: Int) extends TimeUnitShifter {
  val timeUnit = YearUnit
  
  override def toString = "%+d".format(step) + " " + YearUnit.toString

  override def adjustInto(temporal: Temporal): Temporal = temporal.plus(step, ChronoUnit.YEARS)
}

case class MonthShifter(step: Int) extends TimeUnitShifter {
  val timeUnit = MonthUnit


  //  def shift(t: Date) = {
//    var newDay = t.getDayOfMonth
//    var newMonth = t.getMonthValue + step
//    var newYear = t.getYear
//
//    // tritt nur bei Subtraktion auf
//    if (newMonth < 1)
//      newYear += ((newMonth / 12) - 1) // DIV 12
//
//    // tritt nur bei Addition auf
//    if (newMonth > 12)
//      newYear += ((newMonth - 1) / 12) // DIV 12
//
//    newMonth %= 12 // MOD 12
//
//    // Die Berechnung newMonth % 12 kann 0 ergeben. Damit muss zumindest der
//    // Fall newMonth == 0 abgefangen werden!
//    if (newMonth < 1)
//      newMonth += 12
//
//    if (!Date.isValid(newYear, newMonth, newDay)) {
//      newDay = Date.daysIn(newYear, newMonth)
//    }
//    Date.of(newYear, newMonth, newDay)
//  }

  override def toString = "%+d".format(step) + " " + MonthUnit.toString

  override def adjustInto(temporal: Temporal): Temporal = temporal.plus(step, ChronoUnit.MONTHS)

//  override def shift(t: time.Date): time.Date = adjustInto(t)
}


case class DateComponentShifter(val step: Int, dateComponent: DateComponent) extends Shifter {
  def shift(t: Date) = { throw new UnsupportedOperationException() }

  override def toString = "%+d".format(step) + " " + dateComponent.toString

  override def direction = Direction(step)

  override def adjustInto(temporal: Temporal): Temporal = ???
}


object TimeUnitShifter {
  def apply(step: Int, timeUnit: TimeUnit): TimeUnitShifter = timeUnit match {
    case DayUnit                => DayShifter(step)
    case WeekUnit               => WeekShifter(step)
    case MonthUnit              => MonthShifter(step)
    case YearUnit               => YearShifter(step)
    //case dc: NamedDateComponent => new DateComponentShifter(step, dc)
  }
  def unapply(shifter: Shifter): Option[(Int, TimeUnit)] = PartialFunction.condOpt(shifter) {
    case DayShifter(step)  => (step, DayUnit)
    case WeekShifter(step) => (step, WeekUnit)
    case YearShifter(step) => (step, YearUnit)
  }
}

