package de.juergens.time

import java.time.temporal.TemporalAdjuster

import de.juergens.util

/**
 *
 */
@FunctionalInterface
trait Shifter extends TemporalAdjuster {
  @deprecated
  def direction: util.Direction

  def shift(t: java.time.LocalDate): java.time.LocalDate
}
