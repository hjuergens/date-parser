package de.juergens.text.quantlib

import java.time.temporal.{Temporal, TemporalAccessor, TemporalQuery}

import org.jquantlib.time.{Date => QL_Date}

import scala.language.implicitConversions

object QLDate extends TemporalQuery[QL_Date]{
  override def queryFrom(temporal: TemporalAccessor): QL_Date = {
    new QL_Date  (
      temporal.get(java.time.temporal.ChronoField.DAY_OF_MONTH),
      temporal.get(java.time.temporal.ChronoField.MONTH_OF_YEAR),
      temporal.get(java.time.temporal.ChronoField.YEAR))
  }
}

trait DecorateAsQuantLibTime {

  implicit def toQLdate(temporal: Temporal): QL_Date = {
    QLDate.queryFrom(temporal)
  }
}



