package de.juergens.time

import java.time.LocalDate
import java.time.temporal.{Temporal, TemporalAdjuster}

import de.juergens.util.Direction

/**
 * Created by juergens on 27.05.15.
 */
trait Seek extends Shifter with ((Temporal)=>Temporal)


object Seek {
  //def apply(ordinal: Ordinal, attribute: Attribute, dir:Direction) :Seek = WeekDaySeek
  implicit class SeekExtended(temporalAdjuster:TemporalAdjuster) extends Seek {
    override def adjustInto(temporal: Temporal): Temporal = temporalAdjuster.adjustInto(temporal)

    override def apply(t: Temporal): Temporal = adjustInto(t)

    override def shift(t: LocalDate): LocalDate = LocalDate.from(adjustInto(t))

    @deprecated
    override def direction: Direction = de.juergens.util.Up
  }

}

