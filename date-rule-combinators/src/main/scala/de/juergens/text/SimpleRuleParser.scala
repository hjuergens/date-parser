package de.juergens.text

import java.time.temporal._
import java.time.{DayOfWeek, LocalDate => Date}

import de.juergens.finance
import de.juergens.time._
import de.juergens.util.{Minus, Plus, Sign}


// "year + 1 > month 3 > day 14"
// "wednesday -1"
import scala.util.parsing.combinator._
class SimpleRuleParser extends JavaTokenParsers {
  def expr: Parser[DateRule] = repsep(term, "|") ^^
    {y => new UnionRule(y)}
  def term: Parser[DateRule] = repsep(factor, "&") ^^
    {x => new IntersectionRule(x)}
  def factor: Parser[DateRule] = unitRule^^{x=>x} | "("~>repsep(expr, ">")<~")" ^^
    {z => new ListRule(z)}  | repsep(expr, ">") ^^ {z => new ListRule(z)}

  def shift: Parser[(Sign,Int)] = ("+" | "-")~wholeNumber ^^
    { x=>(Sign(x._1),x._2.toInt) }

  def number: Parser[Int] = wholeNumber ^^ {x=>x.toInt} | "" ^^ {_=>0.intValue()}

  def weekDay : Parser[DayOfWeek] =
    ("monday"|"tuesday"|"wednesday"|"thursday"|"friday"|"saturday"|"sunday") ^^
    { str => DayOfWeek.valueOf(str.toUpperCase) }

  private def month3 : Parser[java.time.Month] =
    ("Jan" | "Feb" | "Mar" | "Apr" |"May" | "Jun" |
      "Jul" | "Aug" | "Sep" | "Oct" | "Nov" | "Dec" ) ^^
      { MonthFormat("MMM")(_) }
  private def monthLong : Parser[java.time.Month] =
    ("januar" | "februar" | "march"| "april" | "may" | "june" |
      "jule" | "august" | "september" | "novembre" | "december") ^^
      { MonthFormat("MMMM")(_)}
  def month : Parser[java.time.Month] = month3 | monthLong

  def timeUnit : Parser[TemporalUnit] = ("year" | "quarter" | "month" | "week" | "day") ^^
    { TimeUnit(_) }

  def dateComponent : Parser[TemporalAccessor] = weekDay | month //| timeUnit

  def weekDayShifter: Parser[LocalDateAdjuster] = weekDay~shift ^^
    { case ~(comp, sh) =>
      sh match {
        case (Plus, i)  => comp.query(TemporalQueries.localDate()).plus(i, ChronoUnit.WEEKS)
        case (Minus, i) => comp.query(TemporalQueries.localDate()).minus(i, ChronoUnit.WEEKS)
      }
    }
  def monthShifter: Parser[LocalDateAdjuster] = month~shift ^^
    { case ~(comp, sh) =>
      sh match {
        case (Plus, i)  => comp.query(TemporalQueries.localDate()).plus(i, ChronoUnit.MONTHS)
        case (Minus, i) => comp.query(TemporalQueries.localDate()).minus(i, ChronoUnit.MONTHS)
      }
    }

  def yearShifter: Parser[LocalDateAdjuster] = "year" ~ shift ^^
    { case ~(comp, sh) =>
      sh match {
        case (Plus, i)  => (l:Temporal) => Date.from(l).plus(i, ChronoUnit.YEARS)
        case (Minus, i) => (l:Temporal) => Date.from(l).minus(i, ChronoUnit.YEARS)
      }
    }

  def dateShifter = weekDayShifter | monthShifter | yearShifter

  def shiftRule: Parser[DateRule] = dateShifter ^^
    { x=> new ShiftRule(x) }

  def fixRule: Parser[DateRule] = dateComponent~number ^^
    { x=> new FixRule(x._2, x._1) }

  def unitRule : Parser[DateRule] = shiftRule | fixRule

  def imm : Parser[DateRule] = "imm" ~ number ^^
    { case ~(_,n) => ShiftRule(finance.IMM(n)) }

  val SignNumber = """(\+|-)?(\d+)""".r
}


object ParseExpr extends SimpleRuleParser {
  def main(args: Array[String]) {
    println("input : " + args(0))
    println(parseAll(expr, args(0)))
  }
}


