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

import java.time.temporal._
import java.time.{LocalDate, _}
import java.util.function.{Predicate => JPredicate}

import de.juergens.time._
import de.juergens.time.impl.{DayShifter, TimeUnitShifter}
import de.juergens.time.predicate.{Attribute, WeekDayPredicate}
import de.juergens.util._

import scala.language.postfixOps
import scala.util.parsing.combinator._


/**
  * Every
  */
trait EveryParsers  extends JavaTokenParsers with ExtendedRegexParsers {
  self : DateRuleParsers =>

  import java.time.LocalDate

  def stream : Parser[LocalDate => Stream[LocalDate]] = "every" ~ ordinalUnit ^^
    { x=> (date:LocalDate) => Stream.iterate(date)(x._2.apply) }
}

/**
 * date: on 27.05.15
  *
  * @author juergens
 *
 */
class DateRuleParsers
  extends JavaTokenParsers
    with NumberParsers
    with DateParsers
    with ExtendedRegexParsers
    with EveryParsers {

  /** PnYnMnD */
  def timeUnitSingular : Parser[TemporalUnit] = ("year" | "month" | "week" | "day" | "quarter") ^^
    {
      case "quarter" => IsoFields.QUARTER_YEARS
      case str : String => ChronoUnit.valueOf((str+"s").toUpperCase)
    }

  def year : Parser[java.time.Year] = "year" ~ """(\d){4}""".r ^^
    { case ~(_, text) => Year.parse(text) }

  import org.threeten.extra.Quarter

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

  def daysOfWeek : Parser[WeekDayPredicate] = repsep(dayOfWeek, "or" | ",") ^^
    { new WeekDayPredicate(_ :_*) }


  def dayUnit : Parser[ChronoUnit] = ("days" | "day(s)") ^^
    { _=> ChronoUnit.DAYS }
  def weekUnit : Parser[ChronoUnit] = ("weeks" | "week(s)") ^^
    { _=> ChronoUnit.WEEKS }
  def monthUnit : Parser[ChronoUnit] = ("months" | "month(s)") ^^
    { _=> ChronoUnit.MONTHS }
  def yearUnit : Parser[ChronoUnit] = ("years" | "year(s)") ^^
    { _=> ChronoUnit.YEARS }
  def hourUnit : Parser[ChronoUnit] = ("hours" | "hour(s)") ^^
    { _=> ChronoUnit.HOURS }
  def minuteUnit : Parser[ChronoUnit] = ("minutes" | "minute(s)") ^^
    { _=> ChronoUnit.MINUTES }
  def dateUnit : Parser[ChronoUnit] = dayUnit | weekUnit | monthUnit | yearUnit
  def timeUnit : Parser[ChronoUnit] = hourUnit | minuteUnit

  // next to last  (als) vorletzter
  // next previous last first
  def lastprevious : Parser[Ordinal] = ("last" ~ ("of"?) | "previous" ~ ("to"?)) ^^
    {_=> Ordinal(-1) }
  def nextToLast : Parser[Ordinal] = "next to last" ^^
    { _=>Ordinal(-2) }
  def firstnext : Parser[Ordinal] =  ("next" | "first") ^^
    {_=> Ordinal(1) }
  def nextPrevious  : Parser[Ordinal] = firstnext | lastprevious

  override def ordinal = super.ordinal | nextPrevious

  def previous : Parser[Direction] = ("next" | "previous" ) ^^
    { Direction(_) }
  def afterOrBefore : Parser[Direction] = ( "after" | "before" | "later" | "prior" ) ^^
    { Direction(_) }
  def direction : Parser[Direction] = afterOrBefore

  def direction2 : Parser[Direction] = ("preceding" |  "following") ^^
    { Direction(_) }

  /** e.g. second friday after */
  def seekDayOfWeek : Parser[LocalDateAdjuster] =  (ordinal?) ~ ( dayOfWeek ~ (direction?) ) ^^
    {
      case optionalOrdinal~ (dayOfWeek ~ optionalDirection) => {
        val ord = optionalOrdinal.getOrElse(Ordinal(1))
        val dir = optionalDirection.getOrElse(Up)
        DayOfWeekAdjuster(ord, dayOfWeek, dir)
      }
    }

  def seekMonth :   Parser[LocalDateAdjuster] = (ordinal?) ~ (monthName ~ (direction?)) ^^
    {
      case optionalOrdinal~ (month ~ optionalDirection) => {
        val ord = optionalOrdinal.getOrElse(Ordinal(1))
        val dir = optionalDirection.getOrElse(Up)
        MonthAdjuster(ord, month, dir)
      }
    }

  /** e.g. three months */
  def periodUnit : Parser[TemporalAmount] = cardinal~dateUnit ^^
    {
      case ~(card,ChronoUnit.DAYS) => Period.ofDays(card)
      case ~(card,ChronoUnit.MONTHS) => Period.ofMonths(card)
      case ~(card,ChronoUnit.YEARS) => Period.ofYears(card)
    }
  def durationUnit : Parser[Duration] = cardinal~dateUnit ^^
    { case ~(card,unit) =>Duration.of(card, unit) }

  def period : Parser[Period] = repsep(periodUnit, "and" | ",") ^^
    { _.foldRight(Period.ZERO)( (amount:TemporalAmount,period:Period) => period.plus(amount))}
  def duration : Parser[Duration] = repsep(durationUnit, "and" | ",") ^^
    { _.foldRight(Duration.ZERO)( (amount:Duration,duration:Duration) => duration.plus(amount))}

  def shifter : Parser[LocalDateAdjuster] = (period | duration) ~ ("from" | direction) ^^
  {
    case ~(alt, dir) => new LocalDateAdjuster() {
      override def adjustInto(temporal: Temporal): Temporal = dir match {
        case Up | "from"  => alt.addTo(temporal)
        case Down => alt.subtractFrom(temporal)
      }
    }
  }

  def ordinalUnit : Parser[LocalDateAdjuster] = ordinal ~ timeUnitSingular ^^
    { x=> TemporalPeriodSeek(x._1, x._2) }

  /** e.g. fourth july */
  def ordinalName : Parser[LocalDateAdjuster] = ordinal ~ (dayOfWeek | monthName | "quarter") ^^
    {
      case ~(ordinal, w:DayOfWeek) => DayOfWeekAdjuster(ordinal.abs, w, Direction(ordinal.toInt))
      case ordinal ~ (m:java.time.Month) => MonthAdjuster(ordinal.abs, m, Direction(ordinal.toInt))
      case ~(ordinal, "quarter") => QuarterAdjuster(ordinal.toInt)
    }

  /** e.g. the fourth of july, 17. day of second quarter */
  def dayOf : Parser[LocalDateAdjuster] = ordinal ~ ("day"?) ~ "of" ~ (monthName | ordinalQuarter) ^^
    {
      case ordinal ~ _ ~ _ ~ (m:Month) =>
        new TemporalAdjusterWrapper(
          (m.adjustInto _).andThen(_.`with`(ChronoField.DAY_OF_MONTH,ordinal))
        )
      case ordinal ~ _ ~ _ ~ (q:Quarter) =>
        new TemporalAdjusterWrapper(
          (q.adjustInto _).andThen(_.`with`(ChronoField.DAY_OF_MONTH,ordinal))
        )
    }

  @deprecated("use ordinalUnit instead", "0.0.3")
  def ordinalTimeUnit : Parser[TimeUnitShifter] = ordinal~timeUnitSingular ^^
    { x=> TimeUnitShifter(x._1.toInt, x._2) }

  import LocalDateAdjuster._


  def selector : Parser[LocalDateAdjuster=>LocalDateAdjuster] =
    ("in" | "of") ~ ("the"?) ~ ("delivery month" | seekMonth | "quarter") ^^
    {
      case ~(_, m:java.time.Month) =>
        (adjuster: LocalDateAdjuster) =>
          MonthAdjuster(Ordinal(1),m,Up).andThen[LocalDate](adjuster)
    }

  /**
    * If July 4 is a Saturday, it is observed on Friday
    * "If July 4 is a Sunday, it is observed on Monday"
    * "If October 10 is a Tuesday, Wednesday or Thursday, it is observed on preceding Monday"
    * "If October 10 is a Friday, it is observed on following Monday"
    **/
  def observe : Parser[LocalDateAdjuster] =
    "if" ~  monthDay ~ "is"  ~ "a" ~ daysOfWeek ~ "," ~ "it is observed on" ~ (direction2?) ~ dayOfWeek ^^
      {
        case "if" ~  monthDay ~ "is"  ~ "a" ~ ds ~ "," ~
          "it is observed on" ~ optDir ~ dayOfWeekDestination => {
          val direction = optDir.getOrElse{
            Direction.fromNumber( ds.weekDay.map(dayOfWeekDestination.getValue - _.getValue).min )
          }
          def preOrPostPone(t: Temporal) : LocalDate =
            if(ds.test(t)) {
              DayOfWeekAdjuster(Ordinal(1), dayOfWeekDestination, direction)(t)
            } else LocalDate.from(t)
          new TemporalAdjusterWrapper(preOrPostPone)
        }
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


  def today : Parser[LocalDateAdjuster] =  ("from"?) ~ "today" ^^ { _ => {
    LocalDateAdjuster((t:Temporal) => LocalDate.now(/*clock*/))
  } }
  def now : Parser[LocalDateAdjuster] =  ("from"?) ~ "now" ^^ { _ => {
    LocalDateAdjuster((_:Temporal) => LocalDateTime.now(/*clock*/).toLocalDate) // TODO LocalDateTime
  } }
  def yesterday :Parser[LocalDateAdjuster] = ("yesterday" | ("the"?) ~ "day before").+  ^^
    { x => DayShifter(-x.length) }
  def tomorrow  : Parser[LocalDateAdjuster] = ("tomorrow" | ("the"?) ~ "day after").+   ^^
    { x => DayShifter(x.length) }

  def tomorrowYesterdayToday : Parser[LocalDateAdjuster] = tomorrow | yesterday | today | now

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
}




