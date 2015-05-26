/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.juergens.rule

import java.util.Calendar

import de.juergens.time.{Date, WeekDay}
//

import de.juergens.Attribute

case class WeekDayPredicate(weekDay:WeekDay, calendar : Calendar = Calendar.getInstance()) extends Predicate[Date] {

  override def toString: String = "is " + weekDay + "?"

  def evaluate(t: Date): Boolean = {
    val Date(y,m,d) = t
    calendar.set(Calendar.YEAR, y)
    calendar.set(Calendar.MONTH, m-1)
    calendar.set(Calendar.DAY_OF_MONTH, d)
    //calendar.set(y,m-1,d)
    weekDay equals calendar.get(Calendar.DAY_OF_WEEK)
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

object WeekDayPredicate {
  def weekDay(calendar : Calendar = Calendar.getInstance())(date:Date) : WeekDay = {
    val Date(y,m,d) = date
    calendar.set(Calendar.YEAR, y)
    calendar.set(Calendar.MONTH, m-1)
    calendar.set(Calendar.DAY_OF_MONTH, d)
    //calendar.set(y,m-1,d)
    WeekDay( calendar.get(Calendar.DAY_OF_WEEK) )
  }
}

