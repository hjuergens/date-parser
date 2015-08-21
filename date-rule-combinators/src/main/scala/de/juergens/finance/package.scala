package de.juergens

import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

package object finance {
  val IMM = TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.WEDNESDAY)
}
