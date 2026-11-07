/*
 */

package de.juergens.text

import java.time.temporal._
import java.time.{DayOfWeek, Month, Year, LocalDate => Date}

import de.juergens.util.{Cardinal, Ordinal}

import scala.util.parsing.combinator._

trait DateParsers
  extends JavaTokenParsers
    with ExtendedRegexParsers
    with NumberParsers {

  def dayOfWeek : Parser[DayOfWeek] =
    RegexParser("""(monday|tuesday|wednesday|thursday|friday|fridays|saturday|sunday)s?""".r) ^^
      { x => DayOfWeekFormat("EEEE")(x.group(1)) }

  private def month3 : Parser[Month] =
    ("Jan"|"Feb"|"Mar"|"Apr"|"May"|"Jun"|"Jul"|"Aug"|"Sep"|"Oct" |"Nov"|"Dec") ^^
      { MonthFormat("MMM")(_) }
  private def monthLong : Parser[Month] =
    ("january" | "february" | "march"| "april" | "may" | "june"
      | "july" | "august" | "september" | "october" | "november" | "december") ^^
      { MonthFormat("MMMM")(_)}
  def monthName : Parser[Month] = month3 | monthLong

  def date : Parser[Date] = RegexParser("""(\d{1,2})\.(\d{1,2})\.(\d{2,4})""".r) ^^
    { x => {
      var year       = Integer.parseInt(x.group(3))
      if(year < 100) year+=2000 // TODO
      val month      = Integer.parseInt(x.group(2))
      val dayOfMonth = Integer.parseInt(x.group(1))
      Date.of(year, month, dayOfMonth)
    } }

  assert( parse(date, "23.07.2015").get equals Date.of(2015,7,23) )

  implicit class LocalDateTemporalQuery(function: (TemporalAccessor) => Date)
        (implicit des:String=s"LocalDateTemporalQuery($function)")
    extends TemporalQuery[Date] with ((TemporalAccessor) => Date)  {
      override def queryFrom(temporal: TemporalAccessor) = function(temporal)
      override def toString = des

    override def apply(t: TemporalAccessor): Date = queryFrom(t)
  }

  import Cardinal.cardinal2Int

  private def monthOrdinal : Parser[TemporalQuery[Date]] = monthName ~ (ordinal|cardinal) ^^
    {
      case m ~ (ord:Ordinal)   => (temporal: TemporalAccessor) => Year.from(temporal).atMonth(m).atDay(ord)
      case m ~ (card:Cardinal) => (temporal: TemporalAccessor) => Year.from(temporal).atMonth(m).atDay(card)
    }

  private def ordinalOfMonth : Parser[TemporalQuery[Date]] = ordinal ~ "of" ~ monthName ^^
    {
      case d ~ "of" ~ m => (temporal: TemporalAccessor) => Year.from(temporal).atMonth(m).atDay(d)
    }

  /**
    * December 3rd
    * fourth of July
    * February 29th
    * 16th of september
    * july 4th
    */
  def monthDay : Parser[TemporalQuery[Date]] = monthOrdinal | ordinalOfMonth
}
   