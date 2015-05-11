/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.juergens.time

import de.juergens.time.{Date => _, _}
import de.juergens.time.{EnrichedDate => Date}
import de.juergens.time.EnrichedDate.{ExtendedDate => Date}
import de.juergens.time.EnrichedDate.isLeap
/**
 *
 * @author juergens
 */
abstract class DateComponent

object DateComponent {
  /**
   * days since 0/1/1
   * @see http://alcor.concordia.ca/~gpkatch/gdate-algorithm.html
   */
  def toNumber(date: Date): Int = {
    val Date(year, month, day) = date
    val m = (month + 9) % 12 /* mar=0, feb=11 */
    val y = year - m / 10 /* if Jan/Feb, year-- */
    y * 365 + y / 4 - y / 100 + y / 400 + (m * 306 + 5) / 10 + (day - 1)
  }

  /**
   * days since 0/1/1  to Date
   * @see http://alcor.concordia.ca/~gpkatch/gdate-algorithm.html
   */
  def fromNumber(g: Long): Date = {
    var y = (10000 * g + 14780) / 3652425
    var ddd = g - (365 * y + y / 4 - y / 100 + y / 400)
    if (ddd < 0) {
      y = y - 1
      ddd = g - (365 * y + y / 4 - y / 100 + y / 400)
    }
    val mi = (100 * ddd + 52) / 3060
    val mm = (mi + 2) % 12 + 1
    y = y + (mi + 2) / 12
    val dd = ddd - (mi * 306 + 5) / 10 + 1
    Date(y.intValue, mm.intValue, dd.intValue)
  }

  /**
   * @param year four-digit
   * @return true only if year is a leap year
   */
  def isLeap(year: Int) = {
    ((year % 4 == 0) && (year % 100 != 0)) || (year % 400 == 0)
  }
}

abstract class NamedDateComponent extends DateComponent

abstract class WeekDay extends NamedDateComponent {
  override final def toString: String = this match {
    case WeekDay(name) => name
  }
}

case object Monday extends WeekDay

case object Tuesday extends WeekDay

case object Wednesday extends WeekDay

case object Thursday extends WeekDay

case object Friday extends WeekDay

case object Saturday extends WeekDay

case object Sunday extends WeekDay

case class Day(day: Int) extends DateComponent

object WeekDay {

  def apply(str: String): WeekDay = str match {
    case "monday"    => Monday
    case "tuesday"   => Tuesday
    case "wednesday" => Wednesday
    case "thursday"  => Thursday
    case "friday"    => Friday
    case "saturday"  => Saturday
    case "sunday"    => Sunday
  }

  def unapply(weekDay: WeekDay): Option[String] = PartialFunction.condOpt(weekDay) {
    case Monday    => "monday"
    case Tuesday   => "tuesday"
    case Wednesday => "wednesday"
    case Thursday  => "thursday"
    case Friday    => "friday"
    case Saturday  => "saturday"
    case Sunday    => "sunday"
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

object Month {
  def apply(str: String): Month = str match {
    case "Jan" | "januar"    => Jan
    case "Feb" | "february"  => Feb
    case "Mar" | "march"     => Mar
    case "Apr" | "april"     => Apr
    case "May" | "may"       => May
    case "Jun" | "june"      => Jun
    case "Jul"               => Jul
    case "Aug"               => Aug
    case "Sep" | "september" => Sep
    case "Oct"               => Oct
    case "Nov"               => Nov
    case "Dec" | "december"  => Dec
  }
  def apply(number: Int): Month = number match {
    case 1  => Jan
    case 2  => Feb
    case 3  => Mar
    case 4  => Apr
    case 5  => May
    case 6  => Jun
    case 7  => Jul
    case 8  => Aug
    case 9  => Sep
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
      case Feb => if (isLeap(aYear)) 29 else 28
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

