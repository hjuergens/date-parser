/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.juergens.time.impl


import java.time.Duration
import java.time.temporal._

import de.juergens.time.{LocalDateAdjuster, _}
import de.juergens.util.{Direction, Up}

import scala.language.implicitConversions

/**
 * @param predicate The Condition
 */
case class DateShifter(predicate: (Temporal) => Boolean, direction:Direction = Up)
  extends TemporalAdjuster
with LocalDateAdjuster{

  override def adjustInto(temporal: Temporal): Temporal = {
    var l = temporal
    while(!predicate(l)) l = l.plus(1,ChronoUnit.DAYS)
    l
  }
}

abstract class TimeUnitShifter(step:Long) extends LocalDateAdjuster {
  override def toString = "%+d".format(step)
}

case class WeekShifter(step: Long) extends TimeUnitShifter(step) {
  override def toString = super.toString + " " + ChronoUnit.WEEKS.toString

  override def adjustInto(temporal: Temporal): Temporal =
    temporal.plus(step, ChronoUnit.WEEKS)
}

case class DayShifter(step: Long) extends TimeUnitShifter(step) {
  override def toString = super.toString + " " + ChronoUnit.DAYS.toString

  override def adjustInto(temporal: Temporal): Temporal =
    temporal.plus(step, ChronoUnit.DAYS)
}

case class YearShifter(step: Long) extends TimeUnitShifter(step) {
  override def toString = super.toString + " " + ChronoUnit.YEARS.toString

  override def adjustInto(temporal: Temporal): Temporal =
    temporal.plus(step, ChronoUnit.YEARS)
}

case class MonthShifter(step: Long) extends TimeUnitShifter(step) {
  override def toString = super.toString + " " + ChronoUnit.MONTHS.toString

  override def adjustInto(temporal: Temporal): Temporal =
    temporal.plus(step, ChronoUnit.MONTHS)

}

object TimeUnitShifter {
  def apply(step: Long, timeUnit: TemporalUnit): TimeUnitShifter = timeUnit match {
    case   ChronoUnit.DAYS               => DayShifter(step)
    case   ChronoUnit.WEEKS              => WeekShifter(step)
    case   ChronoUnit.MONTHS             => MonthShifter(step)
    case   ChronoUnit.YEARS              => YearShifter(step)
  }

  //import PartialFunction.condOpt
  //def unapply(shifter: Shifter): Option[(Long, TemporalUnit)] = condOpt(shifter) {
  //  case DayShifter(step)  => (step, ChronoUnit.DAYS)
  //  case WeekShifter(step) => (step, ChronoUnit.WEEKS)
  //  case YearShifter(step) => (step, ChronoUnit.YEARS)
  //}
}

