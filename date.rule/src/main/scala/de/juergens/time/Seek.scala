package de.juergens.time

import java.time.LocalDate
import java.time.temporal.{Temporal, TemporalAdjuster}

import de.juergens.util.Direction

/**
 * Created by juergens on 27.05.15.
 */
@deprecated("will be replayed by TemporalAdjuster","0.0.3")
trait Seek extends Shifter with ((Temporal)=>Temporal)


object Seek {
  //def apply(ordinal: Ordinal, attribute: Attribute, dir:Direction) :Seek = WeekDaySeek
  implicit class SeekExtended(temporalAdjuster:TemporalAdjuster) extends Seek {
    override def adjustInto(temporal: Temporal): Temporal = temporalAdjuster.adjustInto(temporal)

    override def apply(t: Temporal): Temporal = adjustInto(t)

    @deprecated
    override def direction: Direction = de.juergens.util.Up
  }

}

