/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.juergens.time.predicate

import java.time.DayOfWeek
import java.time.temporal.{ChronoField, TemporalAccessor, TemporalQuery}
import java.util.function

case class WeekDayPredicate(weekDay: DayOfWeek*)
  extends function.Predicate[TemporalAccessor] with TemporalQuery[Boolean] {

  override def toString: String = "is " + weekDay + "?"

  override def test(t: TemporalAccessor): Boolean = queryFrom(t)

  import xuwei_k.Scala2Java8.predicate

  private def evaluate(weekDay: DayOfWeek) : function.Predicate[TemporalAccessor] = (ta: TemporalAccessor) => {
    require(ta.isSupported(ChronoField.DAY_OF_WEEK), s"$ta has to support day-of-week")
    ta.ensuring(_.isSupported(ChronoField.DAY_OF_WEEK))

    ta.get(ChronoField.DAY_OF_WEEK) equals weekDay.getValue
  }

  private val composition = {
    val anyFalse : function.Predicate[TemporalAccessor] = predicate((_:TemporalAccessor)=>false)
    weekDay.map(evaluate).foldLeft(anyFalse){ case (compose,predicate) => compose.or(predicate) }
  }
  override def queryFrom(t: TemporalAccessor): Boolean = composition.test(t)
}
