package de.juergens.time

import java.time.LocalDateTime
import java.time.temporal.{Temporal, TemporalAdjuster}

import scala.language.implicitConversions
/**
 *
 */
trait LocalDateTimeAdjuster extends TemporalAdjuster with java.util.function.Function[Temporal, LocalDateTime] {
  def apply(t: Temporal) : LocalDateTime = LocalDateTime.from(adjustInto(t))
}

/**
 * TemporalAdjusters.ofDateAdjuster( (ld:LocalDateTime)=>ld )
 */
object LocalDateTimeAdjuster {
  implicit class LocalDateTimeAdjusterOfFunction(function: java.util.function.Function[Temporal, LocalDateTime])
                                            (implicit des:String=s"LocalDateTimeAdjusterOfFunction($function)")
    extends LocalDateTimeAdjuster {
      override def adjustInto(temporal: Temporal): Temporal =
        function(LocalDateTime.from(temporal))
      override def apply(t: Temporal) = function(t)

    override def toString = des
  }

  import xuwei_k.Scala2Java8.function
  implicit def apply(func : LocalDateTime => LocalDateTime)(implicit des:String="") : LocalDateTimeAdjuster = {
    function[Temporal,LocalDateTime](func.compose[Temporal](LocalDateTime.from(_)))
  }

  implicit class Lifter(ta:TemporalAdjuster)
    extends LocalDateTimeAdjuster {
    override def adjustInto(temporal: Temporal): Temporal = ta.adjustInto(temporal)

    override def apply(t: Temporal) = LocalDateTime.from(adjustInto(t))
  }
}
