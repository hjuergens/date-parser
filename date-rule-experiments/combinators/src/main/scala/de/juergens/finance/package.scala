package de.juergens

import java.time.DayOfWeek
import java.time.temporal.{ChronoField, Temporal, TemporalAdjuster, TemporalAdjusters}

package object finance {
  val IMM :TemporalAdjuster = TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.WEDNESDAY)
  def IMM(m:Int) : TemporalAdjuster = new TemporalAdjuster{
    override def adjustInto(temporal: Temporal): Temporal = {
      temporal
        .`with`(ChronoField.MONTH_OF_YEAR, 3*m)
        .`with`(IMM)

    }
  }
}
