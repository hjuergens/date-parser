package de.juergens.time

import java.time.LocalDate
import java.time.temporal.{Temporal, TemporalAdjuster}

import scala.language.implicitConversions
/**
 *
 */
trait LocalDateAdjuster extends TemporalAdjuster with java.util.function.Function[Temporal, LocalDate] {
  def apply(t: Temporal) : LocalDate = LocalDate.from(adjustInto(t))
}

/**
 * TemporalAdjusters.ofDateAdjuster( (ld:LocalDate)=>ld )
 */
object LocalDateAdjuster {
  implicit class LocalDateAdjusterOfFunction(function: java.util.function.Function[Temporal, LocalDate])
                                            (implicit des:String=s"LocalDateAdjusterOfFunction($function)")
    extends LocalDateAdjuster {
      override def adjustInto(temporal: Temporal): Temporal =
        function(LocalDate.from(temporal))
      override def apply(t: Temporal) = function(t)

    override def toString = des
  }

  import xuwei_k.Scala2Java8.function
  implicit def apply(func : LocalDate => LocalDate)(implicit des:String="") : LocalDateAdjuster = {
    function[Temporal,LocalDate](func.compose[Temporal](LocalDate.from(_)))
  }
//    new LocalDateAdjusterOfFunction(function.compose[Temporal](LocalDate.from(_)))

  implicit class Lifter(ta:TemporalAdjuster)
    extends LocalDateAdjuster {
    override def adjustInto(temporal: Temporal): Temporal = ta.adjustInto(temporal)

    override def apply(t: Temporal) = LocalDate.from(adjustInto(t))
  }
}
