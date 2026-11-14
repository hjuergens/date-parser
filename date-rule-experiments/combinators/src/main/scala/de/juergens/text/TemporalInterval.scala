package de.juergens.text

import java.time.{LocalDate, Instant, Month}
import java.time.temporal.TemporalAdjusters

import org.threeten.extra.Interval

object TemporalInterval {
  def fromMonth(month: Month) : Interval = {
    val startInclusive = LocalDate.now().`with`(month).`with`(TemporalAdjusters.firstDayOfMonth())

    val endExclusive = LocalDate.now().`with`(month).`with`(TemporalAdjusters.firstDayOfNextMonth())
    // TODO
    Interval.of(Instant.from(startInclusive), Instant.from(endExclusive))
  }

}
