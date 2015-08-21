package de.juergens.time

import java.time.chrono.{ChronoLocalDate, Era, IsoChronology}
import java.time.format.DateTimeFormatter
import java.time.temporal._
import java.time.{LocalDate => JLocalDate, Month => JMonth, Period => JPeriod, _}

import scala.language.implicitConversions

@deprecated("use java.time.LocalDate insted", "0.0.3")
object Date {
  def apply(year: Int, month: Int, dayOfMonth: Int): JLocalDate = java.time.LocalDate.of(year, month, dayOfMonth)
}

