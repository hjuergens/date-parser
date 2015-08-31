/*
 * Copyright 2015 Hartmut Jürgens
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.juergens.text

import java.time.{LocalDate, _}
import java.time.temporal._
import java.util.Objects
import java.util.function.{Predicate => JPredicate}

import de.juergens.time._
import de.juergens.time.impl.{DayShifter, TimeUnitShifter}
import de.juergens.util._
import org.threeten.extra.Quarter

import scala.language.postfixOps
import scala.util.parsing.combinator._



/**
 * date: on 27.05.15
 * @author juergens
 *
 */
class DateRuleParsers extends JavaTokenParsers with NumberParsers with ExtendedRegexParsers {
//  def shift: Parser[(Sign,Int)] = ("+" | "-")~wholeNumber ^^ {x=>(Sign(x._1),x._2.toInt)}
//  def number: Parser[Int] = wholeNumber ^^ {x=>x.toInt} | "" ^^ {_=>0.intValue()}

  def dayOfWeek : Parser[DayOfWeek] = ("monday" | "tuesday" |"wednesday"|"thursday"|"friday"|"saturday" |"sunday") ^^ { DayOfWeekFormat("EEEE") }

  private def month3 : Parser[java.time.Month] = ("Jan" | "Feb" | "Mar" | "Apr" |"May" | "Jun" | "Jul" | "Aug" | "Sep" | "Oct" | "Nov" | "Dec" ) ^^ { MonthFormat("MMM")(_) }
  private def monthLong : Parser[java.time.Month] = ("march"| "april" | "may" | "june" | "august" | "september" | "december") ^^ { MonthFormat("MMMM")(_)}
  def monthName : Parser[java.time.Month] = month3 | monthLong

  /** PnYnMnD */
  def timeUnitSingular : Parser[TemporalUnit] = ("year" | "month" | "week" | "day" | "quarter") ^^
    {
      case "quarter" => IsoFields.QUARTER_YEARS
      case str : String => ChronoUnit.valueOf((str+"s").toUpperCase)
    }

  def year : Parser[java.time.Year] = "year" ~ """(\d){4}""".r ^^
    { case ~(_, text) => Year.parse(text) }

  // IsoFields.QUARTER_OF_YEAR
  def ordinalQuarter : Parser[Quarter] = ("first"|"second"|"third"|"fourth") ~ "quarter" ^^
    {
      case ~("first",_) => Quarter.Q1
      case ~("second",_) => Quarter.Q2
      case ~("third",_) => Quarter.Q3
      case ~("fourth",_) => Quarter.Q4
    }

  def quarter : Parser[(Temporal)=>Quarter] = "quarter" ^^
    { case _ => Quarter.from _  }

  def temporalAccessor : Parser[TemporalAccessor] = dayOfWeek | monthName | year | ordinalQuarter

  def attribute : Parser[JPredicate[TemporalAccessor]] = temporalAccessor ^^
    { (accessor: TemporalAccessor)  => new Attribute(accessor) }

  //def timeUnitSingular : Parser[TemporalUnit] = ("year" | "quarter" | "month"| "week" | "day") ^^ {TimeUnit(_)}

  //def _timeUnit : Parser[TemporalUnit] = ("years" | "quarters" | "months"| "weeks" | "days") ^^ {TimeUnit(_)}
  def dayUnit : Parser[ChronoUnit] = ("days" | "day(s)") ^^
    { _=> ChronoUnit.DAYS }
  def weekUnit : Parser[ChronoUnit] = ("weeks" | "week(s)") ^^
    { _=> ChronoUnit.WEEKS }
  def monthUnit : Parser[ChronoUnit] = ("months" | "month(s)") ^^
    { _=> ChronoUnit.MONTHS }
  def yearUnit : Parser[ChronoUnit] = ("years" | "year(s)") ^^
    { _=> ChronoUnit.YEARS }
  def timeUnit : Parser[TemporalUnit] = dayUnit | weekUnit | monthUnit | yearUnit

  // next to last  (als) vorletzter
  // next previous last first
  def lastprevious : Parser[Ordinal] = ("last" | "previous") ^^
    {_=> Ordinal(-1) }
  def nextToLast : Parser[Ordinal] = "next to last" ^^
    { _=>Ordinal(-2) }
  def firstnext : Parser[Ordinal] =  ("next" | "first") ^^
    {_=> Ordinal(1) }
  def nextPrevious  : Parser[Ordinal] = firstnext | lastprevious

  override def ordinal = super.ordinal | nextPrevious

  def previous : Parser[Direction] = ("next" | "previous" ) ^^
    { Direction(_) }
  def afterOrBefore : Parser[Direction] = ( "after" | "before" | "later" | "prior") ^^
    { Direction(_) }
  def direction : Parser[Direction] = afterOrBefore

  
  /** e.g. second friday after */
  def seekDayOfWeek : Parser[LocalDateAdjuster] =  (ordinal?) ~ ( dayOfWeek ~ (direction?) ) ^^
    {
      triple => {
        val ord = triple._1.getOrElse(Ordinal(1))
        val dir = triple._2._2.getOrElse(Up)
        DayOfWeekAdjuster(ord, triple._2._1, dir)
      }
    }

  def seekMonth :   Parser[LocalDateAdjuster] = (ordinal?) ~ (monthName ~ (direction?)) ^^
  {
    triple => {
      val ord = triple._1.getOrElse(Ordinal(1))
      val dir = triple._2._2.getOrElse(Up)
      MonthAdjuster(ord, triple._2._1, dir)
    }
  }

  /** e.g. three months */
  def periodUnit : Parser[TemporalAmount] = cardinal~timeUnit ^^
    { case ~(card,unit) => de.juergens.time.Period.of(card.toInt, unit) }
  def period : Parser[TemporalAmount] = repsep(periodUnit, "and" | ",") ^^
    { _.foldRight(java.time.Period.ZERO)( (amount:TemporalAmount,period:Period) => period.plus(amount))}

  /** e.g. three months before */
  def seek2 : Parser[TimeUnitShifter] = cardinal~timeUnit~direction ^^
    { triple => TimeUnitShifter(triple._2 * triple._1._1.toLong, triple._1._2) }

  def stream : Parser[LocalDate => Stream[LocalDate]] = "every" ~ ordinalUnit ^^
    { x=> (date:LocalDate) => Stream.iterate(date)(x._2.apply) }

  // next week, next month, next friday, next april, next spring    ordinal: Ordinal, dayOfWeek: DayOfWeek, direction: Direction
  def ordinalUnit : Parser[LocalDateAdjuster] = ordinal ~ timeUnitSingular ^^
    { x=> TemporalPeriodSeek(x._1, x._2) }

  def ordinalName : Parser[LocalDateAdjuster] = ordinal ~ (dayOfWeek | monthName | "quarter") ^^
    {
      case ~(ordinal, w:DayOfWeek) => DayOfWeekAdjuster(ordinal.abs, w, Direction(ordinal.toInt))
      case ~(ordinal, m:java.time.Month) => MonthAdjuster(ordinal.abs, m, Direction(ordinal.toInt))
      case ~(ordinal, "quarter") => QuarterAdjuster(ordinal.toInt)
    }

  @deprecated("use ordinalUnit instead", "0.0.3")
  def ordinalTimeUnit : Parser[TimeUnitShifter] = ordinal~timeUnitSingular ^^
    { x=> TimeUnitShifter(x._1.toInt, x._2) }

  //  day after : timeUnitSingular~direction => seek
  //  the day before  : "the" ~ timeUnitSingular~direction => seek
  //  the monday after  : "the" ~ weekDay~direction => seek
  //  the monday before  : "the" ~ month~direction => seek
  //  2 fridays before  : cardinal~weekDay~direction => seek
  //  4 tuesdays after  : cardinal~weekDay~direction => seek

  //  finding the first or last day of the month
  //  finding the first day of next month
  //  finding the first or last day of the year
  //  finding the first day of next year
  //  finding the first or last day-of-week within a month, such as "first Wednesday in June"
  //  finding the next or previous day-of-week, such as "next Thursday"

  def adjuster : Parser[LocalDateAdjuster] =
    dayOfWeekInMonth | tomorrowYesterdayToday | seekDayOfWeek | seekMonth | ordinalUnit | ordinalName

  // TODO season
  def season : Parser[LocalDateAdjuster] = ("spring" | "summer" | "autum" | "winter") ^^
    {
      case "spring" => MonthDay.of(java.time.Month.FEBRUARY,13)
      case "summer" => MonthDay.of(java.time.Month.FEBRUARY,13)
      case "autumn"|"fall"  => MonthDay.of(java.time.Month.FEBRUARY,13)
      case "winter" => MonthDay.of(java.time.Month.FEBRUARY,13)
    }

  val seasonQuery = new TemporalQuery[(LocalDate,LocalDate)] {
    override def queryFrom(temporal: TemporalAccessor): (LocalDate, LocalDate) = ???
  }

  import LocalDateAdjuster._

  def selector : Parser[LocalDateAdjuster=>LocalDateAdjuster] = ("in"|"of") ~ (seekMonth | "quarter" | season) ^^
    {
      case ~(_, m:java.time.Month) => (adjuster: LocalDateAdjuster) => MonthAdjuster(Ordinal(1),m,Up).andThen[LocalDate](adjuster)
    }

  def dayOfWeekInMonth : Parser[LocalDateAdjuster] = ordinal ~ dayOfWeek ~ "in" ~ monthName ^^
    {
      (x) => new LocalDateAdjuster {
        val ~(~(~(ord,dayOfWeek),_),month) = x

        override def adjustInto(temporal: Temporal): Temporal = {
          temporal.ensuring(_.isSupported(ChronoField.DAY_OF_WEEK))
          temporal.ensuring(_.isSupported(ChronoField.MONTH_OF_YEAR))
          temporal.`with`(month).`with`(ord match {
            case o:Ordinal if o.toInt>0 => TemporalAdjusters.firstInMonth(dayOfWeek)
            case o:Ordinal if o.toInt<0 => TemporalAdjusters.lastInMonth(dayOfWeek)
          })
        }
      }
    }

  def today : Parser[LocalDateAdjuster] =  "today" ^^ { _ => {
    LocalDateAdjuster((t:Temporal) => LocalDate.from(t))
  } }
  def yesterday :Parser[LocalDateAdjuster] = ("yesterday" | "day before").+  ^^ { x => DayShifter(-x.length) }
  def tomorrow  : Parser[LocalDateAdjuster] = ("tomorrow" | "day after").+   ^^ { x => DayShifter(x.length) }

  def tomorrowYesterdayToday : Parser[LocalDateAdjuster] = tomorrow | yesterday | today

  def rule : Parser[Temporal => Stream[Temporal]] = """.*""".r ^^
    { _ => (t:Temporal) => Stream.empty[Temporal]}

  //def inMonthName : Parser[Attribute] = "in" ~ monthName ^^
  //  { (x) => TemporalInterval.fromMonth(x._2) }

  def businessDays : Parser[(Set[Temporal]) => TemporalUnit] = ("""business days?""".r) ^^
    { _=>(holidays: Set[Temporal]) => BusinessDay.unit(holidays) }

  /** e.g. two business days prior */
  def seek3 : Parser[(Set[Temporal]) => TemporalAdjuster] = cardinal ~ businessDays ~ direction ^^
    {
      (x) => (holidays: Set[Temporal]) => new TemporalAdjuster {
        val ~(~(cardinal,businessDays),direction) = x

        override def adjustInto(temporal: Temporal): Temporal = {
          val unit = businessDays(holidays)
          unit.addTo(temporal, direction * cardinal)
        }
      }
    }

}

object ParseExpr$Date extends DateRuleParsers {
  def main(args: Array[String]) {
    test()
    //       println("input : " + args(0))
    //       println(parseAll(expr, args(0)))
  }

  /*
    * Best practice for applications is to pass a {@code Clock} into any method
    * that requires the current instant. A dependency injection framework is one
    * way to achieve this:
    */
  private val clock : Clock = Clock.system(ZoneId.systemDefault())  // dependency inject
  import java.time.temporal.TemporalAdjusters._

  val timePoint : LocalDateTime = LocalDateTime.now(clock)
  Objects.requireNonNull(timePoint, "timePoint")
  val foo = timePoint.`with`(lastDayOfMonth())
  val bar = timePoint.`with`(previousOrSame(DayOfWeek.WEDNESDAY))

  // Using value classes as adjusters
  timePoint.`with`(LocalTime.now())

  def test(): Unit = {
    val today = LocalDateTime.now(clock)
    val theDayBefore = parseAll(adjuster, "the day before").get.adjustInto _

    assert( theDayBefore(today) == today.minusDays(1))
    assert( theDayBefore(today) ==  Duration.ofDays(1).subtractFrom(today))
  }

}


