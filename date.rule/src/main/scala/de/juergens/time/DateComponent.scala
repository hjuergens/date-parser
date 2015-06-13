/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.juergens.time

import java.time.DayOfWeek

/**
 *
 * @author juergens
 */
@deprecated("use java.time instead", "0.0.2")
abstract class DateComponent

/*TODO check extends TemporalField*/


object DateComponent {

  /**
   * @param year four-digit
   * @return true only if year is a leap year
   */
  def isLeap(year: Int) = {
    ((year % 4 == 0) && (year % 100 != 0)) || (year % 400 == 0)
  }
}

abstract class NamedDateComponent extends DateComponent

abstract class WeekDay(nr: Int) extends NamedDateComponent {
  val dayOfWeek: DayOfWeek

  override final def toString: String = this match {
    case WeekDay(name) => name
  }

  def distance(weekDay: WeekDay): Int = weekDay.hashCode() - hashCode()

  override def equals(obj: scala.Any): Boolean = obj match {
    case i: Int => i == hashCode()
    case wd: WeekDay => wd.hashCode == hashCode()
    case _ => false
  }

  override def hashCode() = nr
}

import java.util.Calendar._

case object Monday extends WeekDay(MONDAY) {
  override final val dayOfWeek = DayOfWeek.MONDAY
}

/*2*/

case object Tuesday extends WeekDay(TUESDAY) {
  override final val dayOfWeek = DayOfWeek.TUESDAY
}

case object Wednesday extends WeekDay(WEDNESDAY) {
  override final val dayOfWeek = DayOfWeek.WEDNESDAY
}

case object Thursday extends WeekDay(THURSDAY) {
  override final val dayOfWeek = DayOfWeek.THURSDAY
}

case object Friday extends WeekDay(FRIDAY) {
  override final val dayOfWeek = DayOfWeek.FRIDAY
}

case object Saturday extends WeekDay(SATURDAY) {
  override final val dayOfWeek = DayOfWeek.SATURDAY
}

/*7*/

case object Sunday extends WeekDay(SUNDAY) {
  override final val dayOfWeek = DayOfWeek.SUNDAY
}

/*1*/

case class Day(day: Int) extends DateComponent

object WeekDay {

  def apply(any: Any): WeekDay = any match {
    case "monday" | MONDAY => Monday
    case "tuesday" | TUESDAY => Tuesday
    case "wednesday" | WEDNESDAY => Wednesday
    case "thursday" | THURSDAY => Thursday
    case "friday" | FRIDAY => Friday
    case "saturday" | SATURDAY => Saturday
    case "sunday" | SUNDAY => Sunday
  }

  def unapply(weekDay: WeekDay): Option[String] = PartialFunction.condOpt(weekDay) {
    case Monday => "monday"
    case Tuesday => "tuesday"
    case Wednesday => "wednesday"
    case Thursday => "thursday"
    case Friday => "friday"
    case Saturday => "saturday"
    case Sunday => "sunday"
  }
}

abstract class Month extends NamedDateComponent {
  override final def toString: String = this match {
    case Month(name) => name
  }
}

case object Jan /*(year:Year)*/ extends Month

case object Feb /*(year:Year)*/ extends Month

case object Mar /*(year:Year)*/ extends Month

case object Apr /*(year:Year)*/ extends Month

case object May /*(year:Year)*/ extends Month

case object Jun /*(year:Year)*/ extends Month

case object Jul /*(year:Year)*/ extends Month

case object Aug /*(year:Year)*/ extends Month

case object Sep /*(year:Year)*/ extends Month

case object Oct /*(year:Year)*/ extends Month

case object Nov /*(year:Year)*/ extends Month

case object Dec /*(year:Year)*/ extends Month

@deprecated("use java.time.Month instead", "0.0.2")
object Month {
  def apply(str: String): Month = str match {
    case "Jan" | "januar" => Jan
    case "Feb" | "february" => Feb
    case "Mar" | "march" => Mar
    case "Apr" | "april" => Apr
    case "May" | "may" => May
    case "Jun" | "june" => Jun
    case "Jul" => Jul
    case "Aug" => Aug
    case "Sep" | "september" => Sep
    case "Oct" => Oct
    case "Nov" => Nov
    case "Dec" | "december" => Dec
  }

  def apply(number: Int): Month = number match {
    case 1 => Jan
    case 2 => Feb
    case 3 => Mar
    case 4 => Apr
    case 5 => May
    case 6 => Jun
    case 7 => Jul
    case 8 => Aug
    case 9 => Sep
    case 10 => Oct
    case 11 => Nov
    case 12 => Dec
  }

  def unapply(month: Month): Option[String] = PartialFunction.condOpt(month) {
    case Jan => "Jan"
    case Feb => "Feb"
    case Mar => "Mar"
    case Apr => "Apr"
    case May => "May"
    case Jun => "Jun"
    case Jul => "Jul"
    case Aug => "Aug"
    case Sep => "Sep"
    case Oct => "Oct"
    case Nov => "Nov"
    case Dec => "Dec"
  }

  def daysIn(month: Month, aYear: Int): Int = {
    month match {
      case Jan => 31
      case Feb => if (DateComponent.isLeap(aYear)) 29 else 28
      case Mar => 31
      case Apr => 30
      case May => 31
      case Jun => 30
      case Jul => 31
      case Aug => 31
      case Sep => 30
      case Oct => 31
      case Nov => 30
      case Dec => 31
    }
  }
}

