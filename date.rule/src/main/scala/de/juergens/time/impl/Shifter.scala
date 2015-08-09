/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.juergens.time.impl


import java.time.Duration
import java.time.temporal.{TemporalAccessor, TemporalUnit, ChronoUnit, Temporal}

import de.juergens.time._
import de.juergens.util.{Direction, Up}

import scala.language.implicitConversions

/**
 * @param predicate The Condition
 */
case class DateShifter(predicate: (Temporal) => Boolean, direction:Direction = Up) extends Shifter {

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
}

case class WeekShifter(step: Int) extends TimeUnitShifter {
  val timeUnit = WeekUnit
  
  override def toString = "%+d".format(step) + " " + WeekUnit.toString

  override def adjustInto(temporal: Temporal): Temporal =
    temporal.plus(step, ChronoUnit.WEEKS)
}

case class DayShifter(step: Int) extends TimeUnitShifter {
  val timeUnit = DayUnit 
  
  override def toString = "%+d".format(step) + " " + DayUnit.toString

  override def adjustInto(temporal: Temporal): Temporal =
    temporal.plus(step, ChronoUnit.DAYS)
}

case class YearShifter(step: Int) extends TimeUnitShifter {
  val timeUnit = YearUnit
  
  override def toString = "%+d".format(step) + " " + YearUnit.toString

  override def adjustInto(temporal: Temporal): Temporal =
    temporal.plus(step, ChronoUnit.YEARS)
}

case class MonthShifter(step: Int) extends TimeUnitShifter {
  val timeUnit = MonthUnit

  override def toString = "%+d".format(step) + " " + MonthUnit.toString

  override def adjustInto(temporal: Temporal): Temporal =
    temporal.plus(step, ChronoUnit.MONTHS)

}

object QuarterUnit extends TemporalUnit {
  override def addTo[R <: Temporal](temporal: R, amount: Long): R = ???

  override def between(temporal1Inclusive: Temporal, temporal2Exclusive: Temporal): Long = ???

  override def isTimeBased: Boolean = ???

  override def getDuration: Duration = ???

  @Override
  def isDurationEstimated : Boolean = true

  @Override
  def isDateBased() : Boolean = true
}

case class QuarterShifter(step: Int) extends TimeUnitShifter {
  val timeUnit = null

  override def toString = "%+d".format(step) + " " + QuarterUnit.toString

  override def adjustInto(temporal: Temporal): Temporal =
    temporal.plus(step, QuarterUnit)
}


case class DateComponentShifter(step: Int, dateComponent: DateComponent) extends Shifter {

  override def toString = "%+d".format(step) + " " + dateComponent.toString

  override def direction = Direction(step)

  override def adjustInto(temporal: Temporal): Temporal = ???
}


object TimeUnitShifter {
  def apply(step: Int, timeUnit: TemporalUnit): TimeUnitShifter = timeUnit match {
    case ChronoUnit.DAYS                => DayShifter(step)
    case   ChronoUnit.WEEKS              => WeekShifter(step)
    case   ChronoUnit.MONTHS             => MonthShifter(step)
    case   ChronoUnit.YEARS              => YearShifter(step)
    //case dc: NamedDateComponent => new DateComponentShifter(step, dc)
  }
  import PartialFunction.condOpt
  def unapply(shifter: Shifter): Option[(Int, TimeUnit)] = condOpt(shifter) {
    case DayShifter(step)  => (step, DayUnit)
    case WeekShifter(step) => (step, WeekUnit)
    case YearShifter(step) => (step, YearUnit)
  }
}

