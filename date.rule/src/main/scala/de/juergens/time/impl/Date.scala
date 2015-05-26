package de.juergens.time.impl

import de.juergens._
import de.juergens.time.{Month, Feb, Date, EnrichedDate}
import org.joda.time.LocalDate

/*
object `package` {
  type Date = JodaDate.Date
  //   implicit def fromInt(i: Int) = Num(i)
}
*/

/**
 * Created by juergens on 10.05.15.
 */
//class JodaDate(val localDate:LocalDate) extends time.Date

object Joda {
  def isValid(year: Int, month: Int, dayOfMonth: Int): Boolean = dayOfMonth <= daysIn(year,month)


  // tODO days in Month
  def daysIn(year: Int, month: Int): Int = {
    Month(month) match {
      case Feb => if(isLeap(year)) 29 else 28
      case _ => 30
    }
  }

  def isLeap(year:Int) : Boolean = false // TODO implement leap


  implicit class ExtendedDate(localDate:LocalDate) extends EnrichedDate {
    type D = ExtendedDate//this.type

    override def +(days: Int): D = {
      val ld = new LocalDate(localDate)
      new ExtendedDate(ld.plusDays(days))
    }

    override def -(days: Int): D = {
      val ld = new LocalDate(localDate)
      new ExtendedDate(ld.minusDays(days))
    }

    override def compare(that: time.Date): Int = {
      val Date(year,month,dayOfMonth) = that
      localDate.compareTo(new LocalDate(year,month,dayOfMonth))
    }

    override def year: Int = localDate.getYear

    override def dayOfMonth: Int = localDate.getDayOfMonth

    override def month: Int = localDate.getMonthOfYear

    override def toString: String = localDate.toString
  }

  def apply(year:Int, month:Int, dayOfMonth:Int) : time.EnrichedDate = {
    new ExtendedDate(new LocalDate(year,month,dayOfMonth))
  }


}

import net.fortuna.ical4j.model.{Date => Ical4jModelDate}
import java.util.{Date => juDate}
//class Ical4jDate(val localDate: Ical4jModelDate) extends time.Date

// TODO enrich Ical4j
object JDate {

  implicit class ExtendedDate(date:juDate) extends time.EnrichedDate {
    override type D = ExtendedDate//this.type

    val cal = java.util.Calendar.getInstance()

    val Date(year,month,dayOfMonth) = date
    cal.set(year,month,dayOfMonth)
    val localDate = new juDate(cal.getTimeInMillis)

    override def +(days: Int): D = {
      cal.roll(java.util.Calendar.DAY_OF_MONTH, days)
      val ld = new juDate(cal.getTimeInMillis)
      new ExtendedDate(ld)
    }

    override def -(days: Int): D = {
      cal.roll(java.util.Calendar.DAY_OF_MONTH, -days)
      val ld = new juDate(cal.getTimeInMillis)
      new ExtendedDate(ld)
    }

    override def compare(that: time.Date): Int = localDate.compareTo(that)

    override def toString: String = localDate.toString
  }

  def apply(year:Int, month:Int, dayOfMonth:Int) : time.EnrichedDate = {
    val cal = java.util.Calendar.getInstance()
    cal.set(year,month,dayOfMonth)
    new ExtendedDate(new juDate(cal.getTimeInMillis))
  }


}

