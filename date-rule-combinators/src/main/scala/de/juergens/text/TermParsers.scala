/*
 */

package de.juergens.text

import java.time
import java.time._
import java.time.temporal._

import de.juergens.time.{PeriodDuration => _}
import org.threeten.extra.DayOfMonth

import scala.util.parsing.combinator._
import scala.language.postfixOps

trait TermParsers extends JavaTokenParsers {

  type BusinessDayConvention = (LocalDate) => LocalDate

  def term: Parser[Any] = periodTerm | date

  def standardPeriod: Parser[Period] = wholeNumber ~ timeUnitTerm ^^ {
    case number ~ ChronoUnit.DAYS => Period.ofDays(number.toInt)
    case number ~ ChronoUnit.WEEKS => Period.ofWeeks(number.toInt)
    case number ~ ChronoUnit.MONTHS => Period.ofMonths(number.toInt)
    case number ~ ChronoUnit.YEARS => Period.ofYears(number.toInt)
  }

  private def infinityPeriod: Parser[Period] = "Infinity" ^^
    { _ => Period.ofYears(Integer.MAX_VALUE) }

  private def lPeriod: Parser[Period] = "L" ^^
    { _ => Period.ofWeeks(3) }

  private def periodSum: Parser[Period] = (standardPeriod *) ^^
    { case periods => periods.foldRight(Period.ZERO)((p, s) => p plus s) }

  def periodTerm: Parser[Period] = infinityPeriod | lPeriod | periodSum

  def periodAdjuster: Parser[BusinessDayConvention => LocalDate => LocalDate]
  = ("+" | "-") ~ periodTerm ^^
    {
      case "+" ~ period =>
        (bdc:BusinessDayConvention) => (date: LocalDate) =>bdc(date.plus(period))
      case "-" ~ period =>
        (bdc:BusinessDayConvention) => (date: LocalDate) =>bdc(date.minus(period))
    }


  def timeUnitTerm: Parser[ChronoUnit] = ("D" | "W" | "M" | "Y") ^^
    {
      case "D" => ChronoUnit.DAYS
      case "W" => ChronoUnit.WEEKS
      case "M" => ChronoUnit.MONTHS
      case "Y" => ChronoUnit.YEARS
    }

  def date: Parser[LocalDate] = year4~"-"~month~"-"~dayOfMonth ^^
    { case year~"-"~month~"-"~dayOfMonth => LocalDate.of(year.getValue,month,dayOfMonth.getValue) }

  def year4: Parser[Year] = """\d\d\d\d""".r ^^
    { str => Year.of(str.toInt) }

  def month: Parser[Month] = """\w{1,3}""".r ^^
    {
      case "Jan" => Month.JANUARY
      case "Feb" => Month.FEBRUARY
      case "Mar" => Month.MARCH
      case "Apr" => Month.APRIL
      case "May" => Month.MAY
      case "Jun" => Month.JUNE
      case "Jul" => Month.JULY
      case "Aug" => Month.AUGUST
      case "Sep" => Month.SEPTEMBER
      case "Oct" => Month.OCTOBER
      case "Nov" => Month.NOVEMBER
      case "Dec" => Month.DECEMBER
    }

  def dayOfMonth: Parser[DayOfMonth] = """\d?\d""".r ^^
    { str => DayOfMonth.of(str.toInt) }

  /** notation of foward rate agreements, e.g 3X6 */
  def fra: Parser[(Period,Period)] = wholeNumber ~ ("x"|"X") ~ wholeNumber ^^
    { case shortLeg ~ (_) ~ longLeg => (Period.ofMonths(shortLeg.toInt),Period.ofMonths(longLeg.toInt))}

  /** delivery months of futures and options */
  def deliveryMonth : Parser[Month]  = """\w{1}""".r ^^ // """\w{1,3}""".r ^^
    {
      case "F" => Month.JANUARY
      case "G" => Month.FEBRUARY
      case "H" => Month.MARCH
      case "J" => Month.APRIL
      case "K" => Month.MAY
      case "M" => Month.JUNE
      case "N" => Month.JULY
      case "Q" => Month.AUGUST
      case "U" => Month.SEPTEMBER
      case "V" => Month.OCTOBER
      case "X" => Month.NOVEMBER
      case "Z" => Month.DECEMBER
    }

  /*
  private def thirdWednesday(yearMonth: YearMonth) =
    yearMonth.atDay(1)
      .`with`(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY))
      .plus(2, ChronoUnit.WEEKS)
  */

  val quaterlies = Seq( Month.MARCH, Month.JUNE, Month.SEPTEMBER, Month.DECEMBER )

  object thirdWednesday extends TemporalAdjuster {
    override def adjustInto(temporal: Temporal): Temporal = {
      val yearMonth = YearMonth.from(temporal)
      yearMonth.atDay(1)
        .`with`(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY))
        .plus(2, ChronoUnit.WEEKS)
    }
  }

  object nextQuarterMonth extends TemporalAdjuster {
    override def adjustInto(temporal: Temporal): Temporal = {
      val currentMonthValue = Month.from(temporal).getValue
      val nm = quaterlies.find(currentMonthValue < _.getValue)
      if(nm.isDefined) {
        temporal.`with`(nm.get)
      } else {
        assert(Month.from(temporal) == Month.DECEMBER)
        temporal.plus(1, ChronoUnit.YEARS)
        temporal.`with`(quaterlies.head)
      }
    }
  }

  object nextQuarterImm extends TemporalAdjuster {
    override def adjustInto(temporal: Temporal): Temporal = {
      val currentDayOfMonth = DayOfMonth.from(temporal)

      val thirdWednesdayInCurrentMonth = temporal.`with`(thirdWednesday)

      if(quaterlies.contains( Month.from(temporal) )) {
        if (currentDayOfMonth.getValue < DayOfMonth.from(thirdWednesdayInCurrentMonth).getValue) {
          temporal.`with`(thirdWednesday)
        } else {
          temporal.`with`(nextQuarterMonth).`with`(thirdWednesday)
        }
      } else {
        temporal.`with`(nextQuarterMonth).`with`(thirdWednesday)
      }
    }
  }

  def imm: Parser[TemporalAdjuster] = """(next )?imm""".r ^^
    { str => nextQuarterImm }

  /** international money market */
  def immTerm: Parser[Year => LocalDate] = deliveryMonth ~ wholeNumber ^^
    {
      case dm ~ y => {
        (anchor) =>
          val yr = (anchor.getValue / 10) * 10 +y.toInt
          LocalDate.from( thirdWednesday.adjustInto(LocalDate.of(yr, dm, 1)) )
      }
    }


}

object ParseTerm extends TermParsers {
  def main(args: Array[String]) {
    println("input : " + args(0))
    println(parseAll(term, args(0)))
  }
}