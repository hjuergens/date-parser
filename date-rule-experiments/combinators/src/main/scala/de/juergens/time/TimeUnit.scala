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

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.juergens.time

import java.time.temporal._
import de.juergens.time.{DateHelper => _ }
import java.time.Duration

import de.juergens.time.{DateHelper => _}

/**
 *
 * @author juergens
 */
object TimeUnit {
  def apply(str: String): TemporalUnit = str match {
    case "day" | "days" | "day(s)" => ChronoUnit.DAYS
    case "week" | "weeks" | "week(s)" => ChronoUnit.WEEKS
    case "month" | "months" | "month(s)" => ChronoUnit.MONTHS
    case "year" | "years" | "year(s)" => ChronoUnit.YEARS
  }

  def unapply(unit: TemporalUnit): Option[String] = PartialFunction.condOpt(unit) {
    case ChronoUnit.DAYS => "day(s)"
    case ChronoUnit.WEEKS => "week(s)"
    case ChronoUnit.MONTHS => "month(s)"
    case ChronoUnit.YEARS => "year(s)"
  }
}
