package de.juergens.text

import java.time.temporal.{TemporalAccessor, TemporalQuery}

import org.jquantlib.time.{Calendar => QLCalendar, Date => QL_Date}

import scala.language.implicitConversions


class IsBusinessDay(calendar : QLCalendar) extends TemporalQuery[Boolean] {
  override def queryFrom(temporal: TemporalAccessor): Boolean =
    calendar.isBusinessDay(QLDate.queryFrom(temporal))
}
