package de.juergens.finance

import java.time.{LocalDate, DayOfWeek}
import java.time.temporal.{TemporalAdjusters, Temporal, TemporalAdjuster}

final class IMM extends TemporalAdjuster with ((Temporal)=>Temporal) {
  val secondWednesday = TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.WEDNESDAY)

  override def adjustInto(temporal: Temporal): Temporal = secondWednesday.adjustInto(temporal)

  // TODO next/previous IMM
  override def apply(t: Temporal): Temporal = adjustInto(t)
}