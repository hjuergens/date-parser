/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.juergens.rule

import java.time.temporal.{ChronoField, Temporal}
import java.time.{DayOfWeek, LocalDate}
import java.util.{Calendar, function}

case class WeekDayPredicate(weekDay: DayOfWeek, calendar: Calendar = Calendar.getInstance())
  extends function.Predicate[Temporal] {

  override def toString: String = "is " + weekDay + "?"

  override def test(t: Temporal): Boolean = evaluate(t)

  def evaluate(t: Temporal): Boolean = {
    require(t.isSupported(ChronoField.DAY_OF_WEEK), s"$t has to support day-of-week")
    t.ensuring(_.isSupported(ChronoField.DAY_OF_WEEK))

    t.get(ChronoField.DAY_OF_WEEK) equals weekDay.getValue
  }
}
