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





import scala.util.parsing.combinator.JavaTokenParsers

trait FinancialParsers extends JavaTokenParsers {
  self : DateRuleParsers =>

  import java.time.temporal.{Temporal, TemporalUnit, TemporalAdjuster}
  import de.juergens.time.BusinessDay

  def londonBusinessDayUnit : Parser[Set[Temporal] => TemporalUnit] = ("london business" ~ dayUnit) ^^
    { case _ => (holidays : Set[Temporal]) => BusinessDay.unit(holidays) }

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

/**
  * Season
  */
trait SeasonParsers  extends JavaTokenParsers with ExtendedRegexParsers {
  self : DateRuleParsers =>

  import java.time.{LocalDate, MonthDay}
  import java.time.temporal.{TemporalAccessor, TemporalQuery}
  import de.juergens.time.temporal.LocalDateAdjuster

  def season : Parser[LocalDateAdjuster] = ("spring" | "summer" | "autumn" | "fall" | "winter") ^^
    {
      case "spring" => MonthDay.of(java.time.Month.FEBRUARY,13)
      case "summer" => MonthDay.of(java.time.Month.FEBRUARY,13)
      case "autumn" | "fall"  => MonthDay.of(java.time.Month.FEBRUARY,13)
      case "winter" => MonthDay.of(java.time.Month.FEBRUARY,13)
    }

  val seasonQuery = new TemporalQuery[(LocalDate,LocalDate)] {
    override def queryFrom(temporal: TemporalAccessor): (LocalDate, LocalDate) = ???
  }

  def season(dayInMonth:Int, month:Int) : String =
  {
    //The condition when the season start
    if (((month == 12)&&(dayInMonth>=16))||((month<=3)&&(dayInMonth<=15))){
      "Winter"
    }
    else if (((month >= 3)&&(dayInMonth>=16))||((month<=6)&&(dayInMonth<=15))){
      "Spring"
    }
    else if (((month >= 6)&&(dayInMonth>=16))||((month<=9)&&(dayInMonth<=15))){
      "Summer"
    }
    else if (((month >= 9)&&(dayInMonth>=16))||((month<=12)&&(dayInMonth<=15))){
      "Fall"
    }
    else "fail"
  }

  /*
  private def getNorthernHemisphereSeasonName(date: java.util.Date) : String = {
    val c: java.util.Calendar = java.util.Calendar.getInstance()
    c.setTime(date)
    c.get(java.util.Calendar.MONTH) match {
      case java.util.Calendar.MARCH=>
      if (c.get(java.util.Calendar.DAY_OF_MONTH) > 20) {
         "Spring"
      } else
       "Winter"
      case java.util.Calendar.APRIL=>
      case java.util.Calendar.MAY=>
       "Spring"
      case java.util.Calendar.JUNE=>
      if (c.get(java.util.Calendar.DAY_OF_MONTH) > 20) {
         "Summer"
      }else
       "Spring"
      case java.util.Calendar.JULY=>
      case java.util.Calendar.AUGUST=>
       "Summer"
      case java.util.Calendar.SEPTEMBER=>
      if (c.get(java.util.Calendar.DAY_OF_MONTH) > 20) {
         "Autumn"
      }else
       "Summer"
      case java.util.Calendar.OCTOBER=>
      case java.util.Calendar.NOVEMBER=>
       "Autumn"
      case java.util.Calendar.DECEMBER=>
      if (c.get(java.util.Calendar.DAY_OF_MONTH) < 21) {
         "Autumn"
      }else "Winter"
      case _ => "Winter"
    }
  }
*/
}

/**
  * This parers make direct use of the functions in [[java.time.temporal.TemporalAdjusters]].
  */
trait TemporalAdjustersParsers  extends JavaTokenParsers with ExtendedRegexParsers {
  self : DateRuleParsers =>

  import java.time.temporal.TemporalAdjusters
  import de.juergens.time.temporal.LocalDateAdjuster

  def firstDayOfMonth : Parser[LocalDateAdjuster] = "first day of month" ^^
    {
      case _ => TemporalAdjusters.firstDayOfMonth()
    }
  def lastDayOfMonth : Parser[LocalDateAdjuster] = "last day of month" ^^
    {
      case _ => TemporalAdjusters.lastDayOfMonth()
    }
  def firstDayOfNextMonth : Parser[LocalDateAdjuster]= "first day of next month" ^^
    {
      case _ => TemporalAdjusters.firstDayOfNextMonth()
    }
  def firstDayOfYear : Parser[LocalDateAdjuster] = "first day of year" ^^
    {
      case _ => TemporalAdjusters.firstDayOfYear()
    }
  def lastDayOfYear : Parser[LocalDateAdjuster] = "last day of year" ^^
    {
      case _ => TemporalAdjusters.lastDayOfYear()
    }
  def firstDayOfNextYear : Parser[LocalDateAdjuster] = "first day of next year" ^^
    {
      case _ => TemporalAdjusters.firstDayOfNextYear()
    }
  def firstInMonth : Parser[LocalDateAdjuster] = "first"~ dayOfWeek ~"in month" ^^
    {
      case ~(~(_,dayOfWeek),_) => TemporalAdjusters.firstInMonth(dayOfWeek)
    }
  def lastInMonth : Parser[LocalDateAdjuster] = "last"~ dayOfWeek ~"in month" ^^
    {
      case ~(~(_,dayOfWeek),_) => TemporalAdjusters.firstInMonth(dayOfWeek)
    }
  def _dayOfWeekInMonth : Parser[LocalDateAdjuster] = ordinal ~ dayOfWeek ~ "in month" ^^
    {
      case ~(~(ord,dayOfWeek),_) => TemporalAdjusters.dayOfWeekInMonth(ord, dayOfWeek)
    }
  def nextDayOfWeek : Parser[LocalDateAdjuster] = "next" ~ dayOfWeek ^^
    {
      case ~(_,dayOfWeek) => TemporalAdjusters.next(dayOfWeek)
    }
  def nextOrSameDayOfWeek : Parser[LocalDateAdjuster] = "next or same" ~ dayOfWeek ^^
    {
      case ~(_,dayOfWeek) => TemporalAdjusters.nextOrSame(dayOfWeek)
    }
  def previousDayOfWeek : Parser[LocalDateAdjuster] = "previous" ~ dayOfWeek ^^
    {
      case ~(_,dayOfWeek) => TemporalAdjusters.previous(dayOfWeek)
    }
  def previousOrSameDayOfWeek : Parser[LocalDateAdjuster] = "previous or same" ~ dayOfWeek ^^
    {
      case ~(_,dayOfWeek) => TemporalAdjusters.previousOrSame(dayOfWeek)
    }

}

