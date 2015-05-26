package de.juergens.time

import de.juergens.util


/**
 *
 */
trait Shifter {
  def direction : util.Direction
  def shift(t: Date): Date
 }
