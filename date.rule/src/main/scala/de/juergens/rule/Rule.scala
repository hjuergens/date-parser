/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.juergens.rule

import java.time.LocalDate
import java.util.Calendar
import java.util.function
import de.juergens.time.WeekDay

//


import java.time.temporal.{ChronoField, Temporal}

case class WeekDayPredicate(weekDay: WeekDay, calendar: Calendar = Calendar.getInstance())
  extends function.Predicate[Temporal] {

  override def toString: String = "is " + weekDay + "?"

  override def test(t: Temporal): Boolean = evaluate(t)

  def evaluate(t: Temporal): Boolean = {
    require(t.isSupported(ChronoField.DAY_OF_WEEK), s"$t has to support day-of-week")
    t.ensuring(_.isSupported(ChronoField.DAY_OF_WEEK))


    assert(t.isInstanceOf[LocalDate])
    t.asInstanceOf[LocalDate].getDayOfWeek // FIXME remove instanceOf

    t.get(ChronoField.DAY_OF_WEEK) equals weekDay
    //    val Date(y,m,d) = t
    //    calendar.set(Calendar.YEAR, y)
    //    calendar.set(Calendar.MONTH, m-1)
    //    calendar.set(Calendar.DAY_OF_MONTH, d)
    //    //calendar.set(y,m-1,d)
    //    weekDay equals calendar.get(Calendar.DAY_OF_WEEK)
  }

  /*
  val evaluate : (Date) => Boolean = new ((Date) => Boolean){
        override def toString = ""
    override def apply(t: Date): Boolean = {
      val Date(y,m,d) = t
      calendar.set(Calendar.YEAR, y)
      calendar.set(Calendar.MONTH, m-1)
      calendar.set(Calendar.DAY_OF_MONTH, d)
      //calendar.set(y,m-1,d)
      weekDay equals calendar.get(Calendar.DAY_OF_WEEK)
    }
  }
  */
}

//object WeekDayPredicate {
//  def weekDay(calendar : Calendar = Calendar.getInstance())(date:Temporal) : WeekDay = {
//    val Date(y,m,d) = date
//    calendar.set(Calendar.YEAR, y)
//    calendar.set(Calendar.MONTH, m-1)
//    calendar.set(Calendar.DAY_OF_MONTH, d)
//    //calendar.set(y,m-1,d)
//    WeekDay( calendar.get(Calendar.DAY_OF_WEEK) )
//  }
//}

