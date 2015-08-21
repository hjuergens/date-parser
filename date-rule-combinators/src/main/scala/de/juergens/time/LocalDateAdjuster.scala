package de.juergens.time

import java.time.LocalDate
import java.time.temporal.{Temporal, TemporalAdjuster}
import java.util.function.UnaryOperator

import de.juergens.util

/**
 *
 */
trait LocalDateAdjuster extends TemporalAdjuster /*with UnaryOperator[LocalDate]*/ with java.util.function.Function[Temporal, LocalDate] {
  def apply(t: Temporal) : LocalDate = LocalDate.from(adjustInto(t))
}

/**
 * TemporalAdjusters.ofDateAdjuster( (ld:LocalDate)=>ld )
 */
object LocalDateAdjuster {
  implicit def apply(function: java.util.function.Function[Temporal, LocalDate])  : LocalDateAdjuster =
    new LocalDateAdjuster {
      override def adjustInto(temporal: Temporal): Temporal =
        function(LocalDate.from(temporal))
      override def apply(t: Temporal) = function(t)
    }

  implicit def apply2(function : Temporal => LocalDate) : LocalDateAdjuster =
  new LocalDateAdjuster {
    override def adjustInto(temporal: Temporal): Temporal =
      function(LocalDate.from(temporal))
    override def apply(t: Temporal) = function(t)
  }

  implicit class Lifter(ta:TemporalAdjuster)
    extends LocalDateAdjuster {
    override def adjustInto(temporal: Temporal): Temporal = ta.adjustInto(temporal)

    override def apply(t: Temporal) = LocalDate.from(adjustInto(t))
  }
}
