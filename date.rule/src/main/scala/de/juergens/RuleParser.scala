package de.juergens

import de.juergens.time._
import scala.util.parsing.input.CharSequenceReader
import java.io.Serializable
import de.juergens.util.Sign
import de.juergens.time.UnionRule
import de.juergens.rule.WeekDayRule


// "year + 1 > month 3 > day 14"
 // "wednesday -1"
 import scala.util.parsing.combinator._
 class RuleParser extends JavaTokenParsers {
    def expr: Parser[DateRule] = repsep(term, "|") ^^ {y => new UnionRule(y)}
    def term: Parser[DateRule] = repsep(factor, "&") ^^ {x => new IntersectionRule(x)}
    def factor: Parser[DateRule] = unitRule^^{x=>x} | "("~>repsep(expr, ">")<~")" ^^ {z => new ListRule(z)}  | repsep(expr, ">") ^^ {z => new ListRule(z)}

    def shift: Parser[(Sign,Int)] = ("+" | "-")~wholeNumber ^^ {x=>(Sign(x._1),x._2.toInt)}
    def number: Parser[Int] = wholeNumber ^^ {x=>x.toInt} | "" ^^ {_=>0.intValue()}

    def weekDay : Parser[WeekDay] = ("monday"|"tuesday"|"wednesday"|"thursday"|"friday"|"saturyday"|"sunday") ^^ { WeekDay(_) }
    def month : Parser[Month] = ("Jan" | "Feb" | "Mar" | "march"| "Apr" | "april" | "May" | "Jun" | "june" | "Jul" | "Aug" | "Sep" | "september" | "Oct" | "Nov" | "Dec" | "december") ^^ {Month(_)}

    def timeUnit : Parser[TimeUnit] = ("year" | "quarter" | "month" | "week" | "day") ^^ { TimeUnit(_) }

    def dateComponent : Parser[DateComponent] = weekDay | month | timeUnit

//      def shiftRule: Parser[DateRule] = dateComponent~shift ^^ {x=> DateComponentShifter(x._2._1 * x._2._2,x._1)}// x=>new ShiftRule(Sign(x._2._1),x._2._2, x._1)}
    def dateShifter: Parser[Shifter] = dateComponent~shift ^^ {x=> DateComponentShifter(x._2._1 * x._2._2,x._1)}
    def shiftRule: Parser[DateRule] = dateShifter ^^ {x=> new ShiftRule(x)}
    def fixRule: Parser[DateRule] = dateComponent~number ^^ {x=>new FixRule(x._2, x._1)}

    def unitRule : Parser[DateRule] = shiftRule | fixRule

   val SignNumber = """(\+|-)?(\d+)""".r
 }


 object ParseExpr extends RuleParser {
    def main(args: Array[String]) {
       println("input : " + args(0))
       println(parseAll(expr, args(0)))
    }
 }




   // "year + 1 > month 3 > day 14"
   // "wednesday -1"
import de.juergens.time.{DateRule=>BaseRule}
import scala.util.parsing.combinator._
class RuleParser2 extends JavaTokenParsers {
//    def expr: Parser[BaseRule] = repsep(term, "|") ^^ {y => new UnionRule(y)}
//    def term: Parser[BaseRule] = repsep(factor, "&") ^^ {x => new IntersectionRule(x)}
//    def factor: Parser[BaseRule] = unitRule^^{x=>x} | "("~>repsep(expr, ">")<~")" ^^ {z => new ListRule(z)}  | repsep(expr, ">") ^^ {z => new ListRule(z)}
//
    def shift: Parser[(Sign,Int)] = ("+" | "-")~wholeNumber ^^ {x=>(Sign(x._1),x._2.toInt)}
    def number: Parser[Int] = wholeNumber ^^ {x=>x.toInt} | "" ^^ {_=>0.intValue()}

    def weekDay : Parser[WeekDayRule] = ("monday"|"tuesday"|"wednesday"|"thursday"|"friday"|"saturyday"|"sunday") ^^ { str => WeekDayRule(WeekDay(str)) }
    def month : Parser[Month] = ("Jan" | "Feb" | "Mar" | "march"| "Apr" | "april" | "May" | "Jun" | "june" | "Jul" | "Aug" | "Sep" | "september" | "Oct" | "Nov" | "Dec" | "december") ^^ {Month(_)}
    def timeUnit : Parser[TimeUnit] = ("year" | "quarter" | "month" | "week" | "day") ^^ {TimeUnit(_)}

//    def dateComponent : Parser[DateComponent] = weekDay | month | timeUnit

//      def shiftRule: Parser[BaseRule] = dateComponent~shift ^^ {x=> DateComponentShifter(x._2._1 * x._2._2,x._1)}// x=>new ShiftRule(Sign(x._2._1),x._2._2, x._1)}
//    def dateShifter: Parser[Shifter] = dateComponent~shift ^^ {x=> DateComponentShifter(x._2._1 * x._2._2,x._1)}
//    def shiftRule: Parser[BaseRule] = dateShifter ^^ {x=> new ShiftRule(x)}
//    def fixRule: Parser[BaseRule] = dateComponent~number ^^ {x=>new FixRule(x._2, x._1)}

//    def unitRule : Parser[BaseRule] = shiftRule | fixRule

//   def relatives: Parser[DateRule] = ("next"|"last"|"3 days from"|"three weeks ago"|"next to last"|"second to last"|"the week before last") ^^ {x=> new ShiftRule(x)}

//   def simpleDay : Parser[DateRule]  =   ("tomorrow"|"yesterday"|"today"|"the day before yesterday"|"the day after tomorrow") ^^ { x=> new DayShifter(x) }

   val SignNumber = """(\+|-)?(\d+)""".r
 }


 object ParseExpr2 extends RuleParser {
    def main(args: Array[String]) {
       println("input : " + args(0))
       println(parseAll(expr, args(0)))
    }
 }

