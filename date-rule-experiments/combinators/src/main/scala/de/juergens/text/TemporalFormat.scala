/*
 * Copyright 2015 Hartmut Jürgens
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.juergens.text

import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.util.Locale

/**
 *  M/L     month-of-year               number/text       7; 07; Jul; July; J
 */
object MonthFormat {
  def apply(pattern:String) = {
    /** is immutable and is thread-safe */
    val formatter = DateTimeFormatter.ofPattern(pattern, Locale.US)

    (str:String) => {
      java.time.Month.of( formatter.parse(str.capitalize).get(ChronoField.MONTH_OF_YEAR) )
    }
  }
}

object DayOfWeekFormat {
  def apply(pattern:String) = {
    /** is immutable and is thread-safe */
    val formatter1 = DateTimeFormatter.ofPattern(pattern, Locale.US)

    (str:String) => DayOfWeek.of( formatter1.parse(str.capitalize).get(ChronoField.DAY_OF_WEEK) )
  }
}



/**
 * Q/q     quarter-of-year             number/text       3; 03; Q3; 3rd quarter
 */
object QuarterOfYearFormat {
  /** is immutable and is thread-safe */
  val formatter = DateTimeFormatter.ofPattern("Q", Locale.US)

  // "q"
  def apply(str: String) = ???
}



