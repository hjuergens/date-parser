package de.juergens.time

import java.time.temporal._
import java.time.{Duration, LocalDate}

/**
 * Created by juergens on 14.08.15.
 */
object BusinessDay  {

  def temporalQuery(holidays : Seq[TemporalAccessor]) = new TemporalQuery[Boolean] {
    override def queryFrom(t: TemporalAccessor) = !holidays.contains(t)
  }

  def unit(holidays : Set[Temporal]) : TemporalUnit = new TemporalUnit {
    override def addTo[R <: Temporal](temporal: R, amount: Long): R = {
      var tmp : Temporal = temporal
      var count = amount
      val sign = Numeric.LongIsIntegral.signum(amount)
      while (count != 0) {
        do {
          tmp = tmp.plus(sign, ChronoUnit.DAYS)
        } while(holidays.contains(tmp))
        count -= sign
      }
      tmp.asInstanceOf[R] // FIXME
    }

    override def isDateBased: Boolean = true

    override def isDurationEstimated: Boolean = true

    override def between(temporal1Inclusive: Temporal, temporal2Exclusive: Temporal): Long = {
      var count = 0L
      val dist = temporal1Inclusive.until(temporal2Exclusive,ChronoUnit.DAYS)
      val sign = Numeric.LongIsIntegral.signum(dist)
      var tmp : Temporal = temporal1Inclusive
      while(tmp != temporal2Exclusive) {
        tmp = tmp.plus(sign, ChronoUnit.DAYS)
        if(!holidays.contains(tmp))
          count += sign
      }
      assert(Math.abs(count) <= dist)
      count
    }

    override def isTimeBased: Boolean = false

    override def getDuration: Duration = Duration.ZERO
  }
}
