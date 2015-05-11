/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.juergens.time

import de.juergens.time.Date

//Ordered[Date]
trait EnrichedDate extends Date {
  def +=(days: Int): EnrichedDate
  def -=(days: Int): EnrichedDate
  def +(days: Int): EnrichedDate
  def -(days: Int): EnrichedDate
  @deprecated
  def year: Int
  @deprecated
  def month: Int
  @deprecated
  def day: Int
}



object EnrichedDate {
  trait DateImplTriple extends EnrichedDate {

    def aYear: Int

    def aMonth: Int

    def aDay : Int

    def +=(days: Int) = new EnrichedDateImpl(aYear, aMonth, aDay + days)
    def -=(days: Int) = new EnrichedDateImpl(aYear, aMonth, aDay - days)
    def +(days: Int) = new EnrichedDateImpl(aYear, aMonth, aDay + days)
    def -(days: Int) = new EnrichedDateImpl(aYear, aMonth, aDay - days)
    def year = aYear
    def month = aMonth
    def day = aYear

    override def equals(obj: scala.Any): Boolean = obj match {
      case d:EnrichedDate => d.year == aYear && d.month == aMonth && d.day== aDay
      case _ => false
    }
  }

  case class EnrichedDateImpl(aYear: Int, aMonth: Int, aDay: Int) extends DateImplTriple

  implicit class ExtendedDate(date:Date) extends Date with DateImplTriple {
    val Date(yyear,mmonth, dday) = date

    def aYear: Int = yyear

    def aMonth: Int = mmonth

    def aDay: Int = dday
  }
  
  def apply(aYear: Int, aMonth: Int, aDay: Int): EnrichedDate = EnrichedDateImpl(aYear,aMonth,aDay)
  def unapply(date: EnrichedDate): Option[(Int, Int, Int)] = Some((date.year, date.month, date.day))
  def isLeap(year: Int): Boolean = false
  def daysIn(year: Int, month: Int): Int = 31
  def isValid(aYear: Int, aMonth: Int, aDay: Int): Boolean = false
}

