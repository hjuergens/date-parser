package de.juergens.time

case class Period(count:Int, unit:TimeUnit)
object Period {
  val Infinity : Period = new Period(Int.MaxValue, null)
}

trait Calendar {
	def advance(date:Date, period:Period) : Date
}

object Calendar {
  def calendarForward : Calendar = new Calendar{
    def advance(date: Date, period: Period): Date = ???
  }
}
