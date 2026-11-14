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

package de.juergens.experimental

import java.time._
import java.util.Objects

import de.juergens.text.DateRuleParsers

object ParseExpr$Date extends DateRuleParsers {
  def main(args: Array[String]) {
    test()
    //       println("input : " + args(0))
    //       println(parseAll(expr, args(0)))
  }

  /*
    * Best practice for applications is to pass a {@code Clock} into any method
    * that requires the current instant. A dependency injection framework is one
    * way to achieve this:
    */
  private val clock : Clock = Clock.system(ZoneId.systemDefault())  // dependency inject
  import java.time.temporal.TemporalAdjusters

  val timePoint : LocalDateTime = LocalDateTime.now(clock)
  Objects.requireNonNull(timePoint, "timePoint")
  val foo = timePoint.`with`(TemporalAdjusters.lastDayOfMonth())
  val bar = timePoint.`with`(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY))

  // Using value classes as adjusters
  timePoint.`with`(LocalTime.now())

  def test(): Unit = {
    val today = LocalDateTime.now(clock)
    val theDayBefore = parseAll(adjuster, "the day before").get.adjustInto _

    assert( theDayBefore(today) == today.minusDays(1))
    assert( theDayBefore(today) ==  Duration.ofDays(1).subtractFrom(today))
  }

}
