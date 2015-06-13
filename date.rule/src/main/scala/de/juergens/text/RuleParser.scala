package de.juergens.text

import de.juergens.time.impl.{DateComponentShifter, TimeUnitShifter}
import de.juergens.time.{UnionRule, _}
import de.juergens.util.Sign

// "year + 1 > month 3 > day 14"
// "wednesday -1"

import scala.util.parsing.combinator._

class RuleParser extends JavaTokenParsers {
  def expr: Parser[DateRule] = repsep(term, "|") ^^ { y => new UnionRule(y) }

  def term: Parser[DateRule] = repsep(factor, "&") ^^ { x => new IntersectionRule(x) }

  def factor: Parser[DateRule] = unitRule ^^ { x => x } | "(" ~> repsep(expr, ">") <~ ")" ^^ { z => new ListRule(z) } | repsep(expr, ">") ^^ { z => new ListRule(z) }

  def unitRule: Parser[DateRule] = shiftRule | fixRule

  def shiftRule: Parser[DateRule] = (dateShifter | unitShifter) ^^ { x => new ShiftRule(x) }
  def fixRule: Parser[DateRule] = dateComponent ~ shift ^^ { x => new FixRule(x._2._1 * x._2._2, x._1) }

  //"week +1"
  def unitShifter: Parser[Shifter] = timeUnit ~ shift ^^ { x => TimeUnitShifter(x._2._1 * x._2._2, x._1) }
  def dateShifter: Parser[Shifter] = dateComponent ~ shift ^^ { x => DateComponentShifter(x._2._1 * x._2._2, x._1) }

  def shift: Parser[(Sign, Int)] = ("+" | "-") ~ wholeNumber ^^ { x => (Sign(x._1), x._2.toInt) }

  def number: Parser[Int] = wholeNumber ^^ { x => x.toInt } | "" ^^ { _ => 0.intValue() }
  def dateComponent: Parser[DateComponent] = weekDay | month // | timeUnit

  def weekDay: Parser[WeekDay] = ("monday" | "tuesday" | "wednesday" | "thursday" | "friday" | "saturyday" | "sunday") ^^ {    WeekDay(_)  }

  def month: Parser[Month] = ("Jan" | "Feb" | "Mar" | "march" | "Apr" | "april" | "May" | "Jun" | "june" | "Jul" | "Aug" | "Sep" | "september" | "Oct" | "Nov" | "Dec" | "december") ^^ {    Month(_)  }

  def timeUnit: Parser[TimeUnit] = ("year" | "month" | "week" | "day") ^^ {    TimeUnit(_)  }



  val SignNumber = """(\+|-)?(\d+)""".r
}


object ParseExpr extends RuleParser {
  def main(args: Array[String]) {
    println("input : " + args(0))
    println(parseAll(expr, args(0)))
  }
}



