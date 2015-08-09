package de.juergens.time

import java.time.LocalDate
import java.time.temporal.TemporalAdjuster

import de.juergens.util

/**
 *
 */
@FunctionalInterface
trait Shifter extends TemporalAdjuster {
  @deprecated
  def direction: util.Direction

  final def shift(t: LocalDate) = LocalDate.from(adjustInto(t))
}
