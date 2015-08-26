package de.juergens.finance

import java.time.{LocalDate, DayOfWeek}
import java.time.temporal.{TemporalAdjusters, Temporal, TemporalAdjuster}

final class IMM extends TemporalAdjuster with ((Temporal)=>Temporal) {
  val thirdWednesday = TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.WEDNESDAY)

  override def adjustInto(temporal: Temporal): Temporal = thirdWednesday.adjustInto(temporal)

  // TODO next/previous IMM
  override def apply(t: Temporal): Temporal = adjustInto(t)
}