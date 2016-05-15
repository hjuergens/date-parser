package de.juergens.text


import java.time.LocalDate
import java.time.temporal.Temporal

import org.jquantlib.time.{Date => QLDate}

import scala.language.implicitConversions


trait DecorateAsJavaTime8 {
  implicit def fromQLdate(date:QLDate) : Temporal = {
    LocalDate.of(date.year(),date.month().value(),date.dayOfMonth())
  }


}