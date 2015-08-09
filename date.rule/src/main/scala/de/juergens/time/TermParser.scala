/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.juergens.time

import scala.util.parsing.combinator._

class TermParser extends JavaTokenParsers {

  type DayOfMonth = Int
  type Month = Int
  type Year = Int

  def term: Parser[Any] = standardPeriod | date

  def standardPeriod: Parser[Period] = wholeNumber ~ timeUnit ^^ {case number ~ timeUnit => Period(number.toInt, timeUnit)} | infinityPeriod

  def infinityPeriod: Parser[Period] = "Infinity" ^^ { case "Infinity" => Period.Infinity }

  def timeUnit: Parser[TimeUnit] = ("D" | "W" | "M" | "Y") ^^ {
    case "D" => DayUnit
    case "W" => WeekUnit
    case "M" => MonthUnit
    case "Y" => YearUnit
  }

  def date: Parser[java.time.LocalDate] = dayOfMonth~"-"~month~"-"~year ^^ { case dayOfMonth~"-"~month~"-"~year => Date(year,month,dayOfMonth) }

  def year: Parser[Year] = """\d\d\d\d""".r ^^ {
    str => str.toInt
  }

  def month: Parser[Month] = """\w{1,3}""".r ^^ {
    case "Jan" => 1
    case "Feb" => 2
    case "Mar" => 3
    case "Apr" => 4
    case "May" => 5
    case "Jun" => 6
    case "Jul" => 7
    case "Aug" => 8
    case "Sep" => 9
    case "Oct" => 10
    case "Nov" => 11
    case "Dec" => 12
  }

  def dayOfMonth: Parser[DayOfMonth] = """\d?\d""".r ^^ { _.toInt }

  def fra: Parser[(Period,Period)] = wholeNumber ~ "x" ~ wholeNumber ^^ { case shortLeg ~ "x" ~ longLeg => (Period(shortLeg.toInt, MonthUnit),Period(longLeg.toInt, MonthUnit))}

  def deliveryMonth : Parser[Month]  = """\w{1,3}""".r ^^ {
    case "F" => 1
    case "G" => 2
    case "H" => 3
    case "J" => 4
    case "K" => 5
    case "M" => 6
    case "N" => 7
    case "Q" => 8
    case "U" => 9
    case "V" => 10
    case "X" => 11
    case "Z" => 12
  }

  // TODO parser for future, consider rollover
  private def thirdWednesday(y:Year,m:Month) = 15
  def future: Parser[(java.time.LocalDate,java.time.LocalDate)] = deliveryMonth ~ wholeNumber ^^ { case dm ~ y =>
    val beginDayOfMonth = thirdWednesday(2000+y.toInt, dm)
    val endDayOfMonth = thirdWednesday(2000+y.toInt, dm+3)
    (Date(2000+y.toInt, dm, beginDayOfMonth),Date(2000+y.toInt, dm+3, endDayOfMonth))
  }
}

object ParseTerm extends TermParser {
  def main(args: Array[String]) {
    println("input : " + args(0))
    println(parseAll(term, args(0)))
  }
}