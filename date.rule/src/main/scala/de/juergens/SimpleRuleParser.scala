package de.juergens

import java.time.temporal._
import java.time.{LocalDate => Date, DayOfWeek}

import de.juergens.rule.{Predicate, WeekDayPredicate}
import de.juergens.time._
import de.juergens.time.impl.{DateComponentShifter, DayShifter, TimeUnitShifter, WeekShifter}
import de.juergens.util.{Cardinal, Ordinal, Direction, Sign}


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
    {x=>(Sign(x._1),x._2.toInt)}
  def number: Parser[Int] = wholeNumber ^^ {x=>x.toInt} | "" ^^
    {_=>0.intValue()}

  def weekDay : Parser[WeekDay] = ("monday"|"tuesday"|"wednesday"|"thursday"|"friday"|"saturyday"|"sunday") ^^
    { WeekDay(_) }

  private def monthFirst3 : Parser[Month] = ("Jan" | "Feb" | "Mar" | "Apr" | "May" | "Jun" ) ^^
    {Month(_)}
  private def monthSecond3 : Parser[Month] = ("Jul" | "Aug" | "Sep" | "Oct" | "Nov" | "Dec") ^^
    {Month(_)}
  private def monthLower : Parser[Month] = ("march"| "april" | "june" | "september" | "december") ^^
    {Month(_)}
  def month : Parser[Month] = monthFirst3 | monthSecond3 | monthLower

  def timeUnit : Parser[TemporalUnit] = ("year" | "quarter" | "month" | "week" | "day") ^^
    { TimeUnit(_) }

  def dateComponent : Parser[DateComponent] = weekDay | month //| timeUnit

  def dateShifter: Parser[Shifter] = dateComponent~shift ^^
      {x=> DateComponentShifter(x._2._1 * x._2._2, x._1)}
  def shiftRule: Parser[DateRule] = dateShifter ^^
    {x=> new ShiftRule(x)}
  def fixRule: Parser[DateRule] = dateComponent~number ^^
    {x=>new FixRule(x._2, x._1)}

  def unitRule : Parser[DateRule] = shiftRule | fixRule

  val SignNumber = """(\+|-)?(\d+)""".r
}


object ParseExpr extends SimpleRuleParser {
  def main(args: Array[String]) {
    println("input : " + args(0))
    println(parseAll(expr, args(0)))
  }
}


