package de.juergens.time.impl

import org.joda.time.LocalDate
import de.juergens._


case class SimpleDate(year:Int, month:Int, dayOfMonth:Int) extends time.Date

//object SimpleDate {
//
//  def apply(year:Int, month:Int, dayOfMonth:Int) : time.Date = {
//    new SimpleDate(year,month,dayOfMonth)
//  }
//
//  def unapply(any :Any) : Option[(Int,Int,Int)] = PartialFunction.condOpt(any){
//    case date : SimpleDate => (date.year ,date.month ,date.dayOfMonth)
//  }
//}

/**
 * Created by juergens on 10.05.15.
 */
class JodaDate(val localDate:LocalDate) extends time.Date

object JodaDate {

  def apply(year:Int, month:Int, dayOfMonth:Int) : time.Date = {
    new JodaDate(new LocalDate(year,month,dayOfMonth))
  }

  def unapply(any :Any) : Option[(Int,Int,Int)] = PartialFunction.condOpt(any) {
    case jd : JodaDate => (jd.localDate.getYear, jd.localDate.getMonthOfYear ,jd.localDate.getDayOfMonth)
  }
}

import net.fortuna.ical4j.model.{Date => Ical4jModelDate}

class Ical4jDate(val localDate: Ical4jModelDate) extends time.Date

object Ical4jDate {

  implicit class Date(date:time.Date) extends time.Date {
    val time.Date(y,m,d) = date
  }

  def apply(year:Int, month:Int, dayOfMonth:Int) : time.Date = {
    val cal = java.util.Calendar.getInstance()
    cal.set(year,month,dayOfMonth)
    new Ical4jDate(new Ical4jModelDate(cal.getTimeInMillis))
  }

  def unapply(any :Any) : Option[(Int,Int,Int)] = PartialFunction.condOpt(any) {
    case jd : Ical4jDate => (jd.localDate.getYear, jd.localDate.getMonth, jd.localDate.getDay)
  }
}

