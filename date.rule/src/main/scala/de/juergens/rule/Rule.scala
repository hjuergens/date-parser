/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.juergens.rule

import de.juergens.time.{WeekDay, Date}
import java.util.Calendar

case class WeekDayRule(weekDay:WeekDay) extends /* WeekDay with*/ Predicate[Date] {

  val calendar = Calendar.getInstance()

  def evaluate(t: Date): Boolean = {
    val Date(y,m,d) = t
    calendar.set(Calendar.YEAR, y)
    calendar.set(Calendar.MONTH, m)
    calendar.set(Calendar.DAY_OF_MONTH, m)
    weekDay equals calendar.get(Calendar.DAY_OF_WEEK)
  }
}

