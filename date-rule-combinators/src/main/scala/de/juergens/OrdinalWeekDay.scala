/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 *
 */

package de.juergens

import java.time.temporal._

import de.juergens.rule.WeekDayPredicate
import de.juergens.time.LocalDateAdjuster
import de.juergens.util.Ordinal

case class Ordinal_WeekDay(ordinal:Ordinal, weekDayPredicate: WeekDayPredicate)
  extends OrdinalAttribute(ordinal, weekDayPredicate.evaluate)
  with LocalDateAdjuster
{

  val Ordinal(number) = ordinal

  val shifter = time.impl.DateShifter(weekDayPredicate.test)

  def forward(count:Int) = new TemporalQuery[Temporal] {
    override def queryFrom(temporal: TemporalAccessor): Temporal =
    {
      TemporalQueries.localDate().queryFrom(temporal)
        .`with`(TemporalAdjusters.nextOrSame(weekDayPredicate.weekDay))
        .plusWeeks(count-1)
    }
  }
  def backward(count:Int) = new TemporalQuery[Temporal] {
    override def queryFrom(temporal: TemporalAccessor): Temporal =
    {
      TemporalQueries.localDate().queryFrom(temporal)
        .`with`(TemporalAdjusters.previousOrSame(weekDayPredicate.weekDay))
        .minusWeeks(count-1)
    }
  }
  override def adjustInto(anchor: Temporal): Temporal = {
    (number match {
      case i if i > 0 => forward(i)
      case i if i < 0 => backward(-i)
      case _ => TemporalQueries.localDate()
    }).queryFrom(anchor)

    /*
    var date = anchor
    val currentWeekday : DayOfWeek = DayOfWeek(anchor.get(ChronoField.DAY_OF_WEEK))
    val destinationWeekday : DayOfWeek  = weekDayPredicate.weekDay
    val dayShifter = DayShifter(destinationWeekday distance currentWeekday match {
      case days if days < 0 => days + 7
      case days if days > 0 => days
    })

    if(number > 0) date = dayShifter.adjustInto(date)
    if(number > 1)
      date = WeekShifter(number-1).adjustInto(date)

    date
    */
  }
}
