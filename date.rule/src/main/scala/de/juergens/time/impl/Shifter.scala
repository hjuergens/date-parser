/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.juergens.time.impl


import de.juergens.rule.Predicate
import de.juergens.time._
import de.juergens.time.Date.date2EnrichedDate
import de.juergens.util.{Direction, Up}
import scala.language.implicitConversions

/**
 * @param predicate The Condition the DateShifter tries to meet by increasing or decreasing input dates.
 */
case class DateShifter(predicate: (Date) => Boolean, direction:Direction = Up) extends Shifter/*extends Serializable*/ {

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

}

abstract class TimeUnitShifter extends Shifter {
  val timeUnit : TimeUnit
  def step : Int
  override val direction = Direction(step)
}

case class WeekShifter(step: Int) extends TimeUnitShifter {
  val timeUnit = WeekUnit
  
  def shift(t: Date) = t + step * 7

  override def toString = "%+d".format(step) + " " + WeekUnit.toString
}

case class DayShifter(step: Int) extends TimeUnitShifter {
  val timeUnit = DayUnit 
  
  def shift(t: Date) = t + step * 1

  override def toString = "%+d".format(step) + " " + DayUnit.toString
}

case class QuarterShifter(step: Int) extends TimeUnitShifter {
  val timeUnit = QuarterUnit 
  
  private val monthShifter = MonthShifter(3 * step)

  def shift(t: Date) = monthShifter.shift(t)

  override def toString = "%+d".format(step) + " " + QuarterUnit.toString
}

case class YearShifter(step: Int) extends TimeUnitShifter {
  val timeUnit = YearUnit
  
  def shift(t: Date) = {
    val Date(year, month, day) = t
    val newYear = year + step
    val shiftedDay = day min Month.daysIn(Month(month), newYear)
    Date(year + step, month, shiftedDay)
  }

  override def toString = "%+d".format(step) + " " + YearUnit.toString
}

case class MonthShifter(step: Int) extends TimeUnitShifter {
  val timeUnit = MonthUnit
  
  def shift(t: Date) = {
    var newDay = t.dayOfMonth
    var newMonth = t.month + step
    var newYear = t.year

    // tritt nur bei Subtraktion auf
    if (newMonth < 1)
      newYear += ((newMonth / 12) - 1) // DIV 12

    // tritt nur bei Addition auf
    if (newMonth > 12)
      newYear += ((newMonth - 1) / 12) // DIV 12

    newMonth %= 12 // MOD 12

    // Die Berechnung newMonth % 12 kann 0 ergeben. Damit muss zumindest der
    // Fall newMonth == 0 abgefangen werden!
    if (newMonth < 1)
      newMonth += 12

    if (!Date.isValid(newYear, newMonth, newDay)) {
      newDay = Date.daysIn(newYear, newMonth)
    }
    Date(newYear, newMonth, newDay)
  }

  override def toString = "%+d".format(step) + " " + MonthUnit.toString
}


case class DateComponentShifter(val step: Int, dateComponent: DateComponent) extends Shifter {
  def shift(t: Date) = { throw new UnsupportedOperationException() }

  override def toString = "%+d".format(step) + " " + dateComponent.toString

  override def direction = Direction(step)
}


object TimeUnitShifter {
  def apply(step: Int, timeUnit: TimeUnit): TimeUnitShifter = timeUnit match {
    case DayUnit                => DayShifter(step)
    case WeekUnit               => WeekShifter(step)
    case MonthUnit              => MonthShifter(step)
    case QuarterUnit            => QuarterShifter(step)
    case YearUnit               => YearShifter(step)
    //case dc: NamedDateComponent => new DateComponentShifter(step, dc)
  }
  def unapply(shifter: Shifter): Option[(Int, TimeUnit)] = PartialFunction.condOpt(shifter) {
    case DayShifter(step)  => (step, DayUnit)
    case WeekShifter(step) => (step, WeekUnit)
    case YearShifter(step) => (step, YearUnit)
  }
}

