package de.juergens

import de.juergens.rule.{Predicate, WeekDayPredicate}
import de.juergens.time.{UnionRule, _}
import de.juergens.time.impl.{DateComponentShifter, DayShifter, TimeUnitShifter, WeekShifter}
import de.juergens.util.{Cardinal, Ordinal, Direction, Sign}


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

    def dateComponent : Parser[DateComponent] = weekDay | month //| timeUnit

//      def shiftRule: Parser[DateRule] = dateComponent~shift ^^ {x=> DateComponentShifter(x._2._1 * x._2._2,x._1)}// x=>new ShiftRule(Sign(x._2._1),x._2._2, x._1)}
    def dateShifter: Parser[Shifter] = dateComponent~shift ^^ {x=> DateComponentShifter(x._2._1 * x._2._2, x._1)}
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




/*
object Ordinal {
  private def parseInt(str:String) : Option[Int] = PartialFunction.condOpt(str) {
    case "second" => 2
    case  s if s endsWith "." => Integer.parseInt(s.substring(0,s.length-1))
  }
  def fromString(str:String) : Ordinal  = parseInt(str).map(Ordinal(_)).getOrElse(throw new IllegalArgumentException(str))

  assert( Ordinal.fromString("second").toInt == 2 )
}
*/

/**
 * attribute : (date) => boolean, (direction) => seek
 */
object Attribute {
  type Attribute = Predicate[Date]
}
import Attribute.Attribute


trait Seek extends Shifter with ((Date)=>Date)

object Seek {
  //def apply(ordinal: Ordinal, attribute: Attribute, dir:Direction) :Seek = WeekDaySeek
}

case class WeekDaySeek(ordinal: Ordinal, attribute: Attribute, override val direction: Direction) extends Seek {

  private def signum = direction
  private val Ordinal(number) = ordinal
  override def shift(t: Date): Date = apply(t)

  def apply(anchor: Date): Date = {
    val shifter = time.impl.DateShifter(attribute, direction)
    var date = anchor

    if (number > 0) {
      date = shifter.shift(date)
    }
    if(date == anchor && number > 0) {
      date = WeekShifter(signum * 1).shift(date)
    }
    if (number > 1) {
      date = WeekShifter(signum * (number - 1)).shift(date)
    }
    date
  }
}


abstract class OrdinalAttribute(ordinal:Ordinal, predicate:(Date)=>Boolean) extends Seek


case class OrdinalWeekDay(ordinal:Ordinal, weekDayPredicate: WeekDayPredicate)
  extends OrdinalAttribute(ordinal, weekDayPredicate.evaluate)
  with Shifter
{

  val Ordinal(number) = ordinal

  val shifter = time.impl.DateShifter(weekDayPredicate)

  override def direction = util.Direction(number)

  override def shift(t: Date): Date = {
    var date = t
    val currentWeekday = WeekDayPredicate.weekDay()(t)
    val destinationWeekday = weekDayPredicate.weekDay
    val dayShifter = DayShifter(destinationWeekday distance currentWeekday match {
      case days if days < 0 => days + 7
      case days if days > 0 => days
    })

    if(number > 0) date = dayShifter.shift(date)
    if(number > 1)
      date = WeekShifter(number-1).shift(date)

    date
  }

  override def apply(v1: Date): Date = shift(v1)
}

   // "year + 1 > month 3 > day 14"
   // "wednesday -1"

import scala.util.parsing.combinator._
class RuleParser2 extends JavaTokenParsers {
//    def expr: Parser[BaseRule] = repsep(term, "|") ^^ {y => new UnionRule(y)}
//    def term: Parser[BaseRule] = repsep(factor, "&") ^^ {x => new IntersectionRule(x)}
//    def factor: Parser[BaseRule] = unitRule^^{x=>x} | "("~>repsep(expr, ">")<~")" ^^ {z => new ListRule(z)}  | repsep(expr, ">") ^^ {z => new ListRule(z)}
//
    def shift: Parser[(Sign,Int)] = ("+" | "-")~wholeNumber ^^ {x=>(Sign(x._1),x._2.toInt)}
    def number: Parser[Int] = wholeNumber ^^ {x=>x.toInt} | "" ^^ {_=>0.intValue()}

    def weekDay : Parser[Attribute] = ("monday"|"tuesday"|"wednesday"|"thursday"|"friday"|"saturyday"|"sunday") ^^ { str => WeekDayPredicate(WeekDay(str)) }
    def attribute : Parser[Attribute] = weekDay

    def month : Parser[Month] = ("Jan" | "Feb" | "Mar" | "march"| "Apr" | "april" | "May" | "Jun" | "june" | "Jul" | "Aug" | "Sep" | "september" | "Oct" | "Nov" | "Dec" | "december") ^^ {Month(_)}
    def timeUnit : Parser[TimeUnit] = ("years" | "quarters" | "months"| "weeks" | "days") ^^ {TimeUnit(_)}
    def timeUnitSingular : Parser[TimeUnit] = ("year" | "quarter" | "(month)s?".r| "week" | "day") ^^ {TimeUnit(_)}

    def ordinal : Parser[Ordinal] = ("first" | "second" | "third") ^^ { Ordinal.fromString(_) }
    def cardinal : Parser[Cardinal] = ("one" | "two" | "three") ^^ { Cardinal.fromString(_) }

    def direction : Parser[Direction] = ("next" | "after" | "before") ^^ { Direction(_) }

    def seek : Parser[Seek] = ordinal~weekDay~direction ^^ { triple => WeekDaySeek(triple._1._1,triple._1._2,triple._2) } | weekDay~direction ^^ { triple => WeekDaySeek(Ordinal(1), triple._1,triple._2) }
    def seek2 : Parser[TimeUnitShifter] = cardinal~timeUnit~direction ^^ { triple => TimeUnitShifter(triple._2 * triple._1._1.toInt, triple._1._2) }

    def ordinalTimeUnit : Parser[TimeUnitShifter] = ordinal~timeUnitSingular ^^ { x=> TimeUnitShifter(x._1.toInt, x._2) }

    def stream : Parser[Date => Stream[Date]] = "every"~ordinalTimeUnit ^^ { x=> (date:Date) => Stream.iterate(date)(x._2.shift) }

    val SignNumber = """(\+|-)?(\d+)""".r

}


 object ParseExpr2 extends RuleParser2 {
    def main(args: Array[String]) {
      test()
//       println("input : " + args(0))
//       println(parseAll(expr, args(0)))
    }

   def test() : Unit = {
     import time.impl.{Joda=>Date}

     println(parseAll(weekDay, "monday"))

     println(parseAll(ordinal, "second"))

     val isFriday : Attribute = parseAll(attribute, "friday").get
     println(isFriday)
     assert( isFriday.evaluate(Date(2015,5,22)) )

     val isMonday : Attribute = parseAll(attribute, "monday").get
     println(isMonday)
     assert( isMonday.evaluate(Date(2015,5,18)) )

     parseAll(ordinalTimeUnit, "second day")

     val anchor = Date(2015,5,23) // saturday
     assert(Saturday == WeekDayPredicate.weekDay()(anchor))

     val secondMondayAfter : Seek = parseAll(seek, "second monday after").get
     assert( Date(2015,6,1) == secondMondayAfter(Date(2015,5,23)))
     assert( Date(2015,6,1) == secondMondayAfter(Date(2015,5,24)))
     assert( Date(2015,6,8) == secondMondayAfter(Date(2015,5,25)))
     assert( Date(2015,6,8) == secondMondayAfter(Date(2015,5,26)))
     println(s"second monday after ${anchor} is ${secondMondayAfter(anchor)}")

     val fridayBefore : Seek = parseAll(seek, "friday before").get
     assert( Date(2015,5,15) == fridayBefore(Date(2015,5,21)) )
     assert( Date(2015,5,15) == fridayBefore(Date(2015,5,22)), fridayBefore(Date(2015,5,22)) )
     assert( Date(2015,5,22) == fridayBefore(Date(2015,5,23)) )
     println(s"friday before ${anchor} is ${fridayBefore(anchor)}")

     val threeMonthsAfter = parseAll(seek2, "three months after").get.shift _
     assert( Date(2015,8,22) == threeMonthsAfter(Date(2015,5,22)), threeMonthsAfter(Date(2015,5,22)) )
     assert( Date(2015,8,30) == threeMonthsAfter(Date(2015,5,30)) )
     assert( Date(2015,2,28) == threeMonthsAfter(Date(2014,11,30)) )

     val everySecondDay = parseAll(stream, "every second day").get.apply _
     assert( Date(2015,5,22) == everySecondDay(Date(2015,5,22))(0) , everySecondDay(Date(2015,5,22))(0) )
     assert( Date(2015,5,24) == everySecondDay(Date(2015,5,22))(1) , everySecondDay(Date(2015,5,22))(1) )
     assert( Date(2015,6,1)  == everySecondDay(Date(2015,5,22))(5) , everySecondDay(Date(2015,5,22))(5) )

     //val nextThirdWednesdayInQarter = parseAll(?, "next third wednesday in quarter")

     //val thirdWednesdayInNextQuarter = parseAll(?, "third wednesday in next quarter")
   }
 }


