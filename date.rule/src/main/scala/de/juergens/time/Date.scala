package de.juergens.time

import de.juergens.time


trait Date

trait EnrichedDate extends Date with Ordered[Date] {
  type D <: EnrichedDate

  def +(days: Int): D
  def -(days: Int): D

  def year: Int

  def month: Int

  def dayOfMonth: Int

  override final def equals(obj: scala.Any): Boolean = obj match {
    case date : EnrichedDate => year == date.year && month == date.month && dayOfMonth == date.dayOfMonth
  }
}

object Date {
  // TODO
  def daysIn(year: Int, month: Int): Int = impl.Joda.daysIn(year, month)

  // TODO
  def isValid(year: Int, month: Int, dayOfMonth: Int) : Boolean = impl.Joda.isValid(year, month, dayOfMonth)

  def unapply(any :Any) : Option[(Int,Int,Int)] = PartialFunction.condOpt(any) {
    case jd : EnrichedDate => (jd.year, jd.month ,jd.dayOfMonth)
  }
  def apply(year:Int, month:Int, dayOfMonth:Int) : time.EnrichedDate = {
    impl.Joda(year,month,dayOfMonth)
  }
  implicit def date2EnrichedDate(date:Date) : EnrichedDate = {
    val Date(year,month,dayOfMonth) = date
    Date(year,month,dayOfMonth)
  }
}

