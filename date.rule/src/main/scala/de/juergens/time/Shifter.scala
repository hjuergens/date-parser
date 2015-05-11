/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.juergens.time

import de.juergens.time.{Date => _}
import de.juergens.time.EnrichedDate.{ExtendedDate => Date}
import de.juergens.time.EnrichedDate.ExtendedDate
import scala.language.implicitConversions

/**
 * @param predicate The Condition the DateShifter tries to meet by increasing or decreasing input dates.
 */
class DateShifter(predicate: Date => Boolean) /*extends Serializable*/ {

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
}

sealed abstract class Sign {
  def *(i: Int): Int

  override final def toString: String = this match {
    case Sign(name) => name
  }
}

object Plus extends Sign {
  def *(i: Int) = i
}

object Minus extends Sign {
  def *(i: Int) = -i
}

object Sign {
  def apply(str: String): Sign = str match {
    case "+" | "plus"  => Plus
    case "-" | "minus" => Minus
  }

  def unapply(unit: Sign): Option[String] = PartialFunction.condOpt(unit) {
    case Plus  => "+"
    case Minus => "-"
  }
}

abstract class Shifter {
  def shift(t: Date): Date
}

case class WeekShifter(step: Int) extends Shifter {
  def shift(t: Date) = t + step * 7

  override def toString = "%+d".format(step) + " " + WeekUnit.toString
}

case class DayShifter(step: Int) extends Shifter {
  def shift(t: Date) = t + step * 1

  override def toString = "%+d".format(step) + " " + DayUnit.toString
}

case class QuarterShifter(aStep: Int) extends Shifter {
  private val monthShifter = MonthShifter(3 * aStep)

  def shift(t: Date) = monthShifter.shift(t)

  override def toString = "%+d".format(aStep) + " " + QuarterUnit.toString
}

case class YearShifter(step: Int) extends Shifter {
  def shift(t: Date) = {
    val Date(year, month, day) = t
    val newYear = year + step
    val shiftedDay = day min Month.daysIn(Month(month), newYear)
    Date(year + step, month, shiftedDay)
  }

  override def toString = "%+d".format(step) + " " + YearUnit.toString
}

case class MonthShifter(step: Int) extends Shifter {
  def shift(t: Date) = {
    var newDay = t.day
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

    if (!EnrichedDate.isValid(newYear, newMonth, newDay)) {
      newDay = EnrichedDate.daysIn(newYear, newMonth)
    }
    Date(newYear, newMonth, newDay)
  }

  override def toString = "%+d".format(step) + " " + MonthUnit.toString
}

class DateComponentShifter(step: Int, dateComponent: NamedDateComponent) extends Shifter {
  def shift(t: Date) = { throw new UnsupportedOperationException() }

  override def toString = "%+d".format(step) + " " + dateComponent.toString
}

object DateComponentShifter {
  def apply(step: Int, dateComponent: DateComponent): Shifter = dateComponent match {
    case DayUnit                => DayShifter(step)
    case WeekUnit               => WeekShifter(step)
    case MonthUnit              => MonthShifter(step)
    case QuarterUnit            => QuarterShifter(step)
    case YearUnit               => YearShifter(step)
    case dc: NamedDateComponent => new DateComponentShifter(step, dc)
  }
  def unapply(shifter: Shifter): Option[(Int, DateComponent)] = PartialFunction.condOpt(shifter) {
    case DayShifter(step)  => (step, DayUnit)
    case WeekShifter(step) => (step, WeekUnit)
    case YearShifter(step) => (step, YearUnit)
  }
}

