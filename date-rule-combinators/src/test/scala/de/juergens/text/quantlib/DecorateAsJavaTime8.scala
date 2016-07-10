package de.juergens.text.quantlib

import java.time.LocalDate
import java.time.temporal.Temporal

import org.jquantlib.time.{Date => QL_Date}

import scala.language.implicitConversions


trait DecorateAsJavaTime8 {
  implicit def fromQLdate(date:QL_Date) : Temporal = {
    LocalDate.of(date.year(),date.month().value(),date.dayOfMonth())
  }


}