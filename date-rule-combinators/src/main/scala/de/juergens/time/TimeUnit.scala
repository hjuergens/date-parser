/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.juergens.time

import java.time.temporal._
import de.juergens.time.{Date => _ }
import java.time.Duration

import de.juergens.time.{Date => _}

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
