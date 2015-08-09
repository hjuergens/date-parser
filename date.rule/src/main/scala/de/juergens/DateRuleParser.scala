package de.juergens

import java.time.temporal._
import java.time.{LocalDate => Date, DayOfWeek}

import de.juergens.rule.{Predicate, WeekDayPredicate}
import de.juergens.time._
import de.juergens.time.impl.{DateComponentShifter, DayShifter, TimeUnitShifter, WeekShifter}
import de.juergens.util.{Cardinal, Ordinal, Direction, Sign}

import scala.util.parsing.combinator.JavaTokenParsers

///**
// * attribute : (date) => boolean, (direction) => seek
// */
//object Attribute {
//  type Attribute = java.util.function.Predicate[Temporal]
//}
//import Attribute.Attribute

class DateRuleParser extends JavaTokenParsers {
//    def expr: Parser[BaseRule] = repsep(term, "|") ^^ {y => new UnionRule(y)}
//    def term: Parser[BaseRule] = repsep(factor, "&") ^^ {x => new IntersectionRule(x)}
//    def factor: Parser[BaseRule] = unitRule^^{x=>x} | "("~>repsep(expr, ">")<~")" ^^ {z => new ListRule(z)}  | repsep(expr, ">") ^^ {z => new ListRule(z)}
//
    def shift: Parser[(Sign,Int)] = ("+" | "-")~wholeNumber ^^ {x=>(Sign(x._1),x._2.toInt)}
    def number: Parser[Int] = wholeNumber ^^ {x=>x.toInt} | "" ^^ {_=>0.intValue()}

    def weekDay : Parser[WeekDayPredicate] = ("monday"|"tuesday"|"wednesday"|"thursday"|"friday"|"saturyday"|"sunday") ^^ { str => WeekDayPredicate(WeekDay(str)) }
    def attribute : Parser[java.util.function.Predicate[Temporal]] = weekDay

    def month : Parser[Month] = ("Jan" | "Feb" | "Mar" | "march"| "Apr" | "april" | "May" | "Jun" | "june" | "Jul" | "Aug" | "Sep" | "september" | "Oct" | "Nov" | "Dec" | "december") ^^ {Month(_)}
    def timeUnit : Parser[ChronoUnit] = ("years" | "quarters" | "months"| "weeks" | "days") ^^ {TimeUnit(_)}
    def timeUnitSingular : Parser[ChronoUnit] = ("year" | "quarter" | "(month)s?".r| "week" | "day") ^^ {TimeUnit(_)}

    def ordinal : Parser[Ordinal] = ("first" | "second" | "third") ^^ { Ordinal.fromString(_) }
    def cardinal : Parser[Cardinal] = ("one" | "two" | "three") ^^ { Cardinal.fromString(_) }

    def direction : Parser[Direction] = ("next" | "after" | "before") ^^ { Direction(_) }

  /**
   * e.g. third wednesday after
   * @return a parser
   */
    private def _seek1 : Parser[DayOfWeekAdjuster] = ordinal~weekDay~direction ^^ { triple => DayOfWeekAdjuster(triple._1._1, triple._1._2.weekDay.dayOfWeek, triple._2) }
    private def _seek2 : Parser[DayOfWeekAdjuster] = weekDay~direction ^^ { triple => DayOfWeekAdjuster(Ordinal(1), triple._1.weekDay.dayOfWeek,triple._2) }
    def seek : Parser[DayOfWeekAdjuster] = _seek1 | _seek2
    def seek2 : Parser[TimeUnitShifter] = cardinal~timeUnit~direction ^^ { triple => TimeUnitShifter(triple._2 * triple._1._1.toInt, triple._1._2) }

    def ordinalTimeUnit : Parser[TimeUnitShifter] = ordinal~timeUnitSingular ^^ { x=> TimeUnitShifter(x._1.toInt, x._2) }

    def stream : Parser[Date => Stream[Date]] = "every"~ordinalTimeUnit ^^ { x=> (date:Date) => Stream.iterate(date)(x._2.shift) }

    val SignNumber = """(\+|-)?(\d+)""".r

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



abstract class OrdinalAttribute(ordinal:Ordinal, predicate:(Date)=>Boolean) extends Seek


case class OrdinalWeekDay(ordinal:Ordinal, weekDayPredicate: WeekDayPredicate)
  extends OrdinalAttribute(ordinal, weekDayPredicate.evaluate)
  with Shifter
{

  val Ordinal(number) = ordinal

  val shifter = time.impl.DateShifter(weekDayPredicate.test)

  override def direction = util.Direction(number)

  override def adjustInto(anchor: Temporal): Temporal = {
    var date = anchor
    val currentWeekday : WeekDay = WeekDay(anchor.get(ChronoField.DAY_OF_WEEK))
    val destinationWeekday : WeekDay  = weekDayPredicate.weekDay
    val dayShifter = DayShifter(destinationWeekday distance currentWeekday match {
      case days if days < 0 => days + 7
      case days if days > 0 => days
    })

    if(number > 0) date = dayShifter.adjustInto(date)
    if(number > 1)
      date = WeekShifter(number-1).adjustInto(date)

    date
  }

  override def apply(v1: Temporal): Temporal = adjustInto(v1)
}

// "year + 1 > month 3 > day 14"
// "wednesday -1"

import scala.util.parsing.combinator._



object ParseExpr$Date extends DateRuleParser {
  def main(args: Array[String]) {
    test()
    //       println("input : " + args(0))
    //       println(parseAll(expr, args(0)))
  }

  def test() : Unit = {
    println(parseAll(weekDay, "monday"))

    println(parseAll(ordinal, "second"))

    val isFriday  = parseAll(attribute, "friday").get
    println(isFriday)
    assert( isFriday.test(Date.of(2015,5,22)) )

    val isMonday  = parseAll(attribute, "monday").get
    println(isMonday)
    assert( isMonday.test(Date.of(2015,5,18)) )

    parseAll(ordinalTimeUnit, "second day")

    val anchor = Date.of(2015,5,23) // saturday
    // TODO     assert(Saturday == WeekDayPredicate.weekDay()(anchor))

    val secondMondayAfter = parseAll(seek, "second monday after").get
    assert( Date.of(2015,6,1) == secondMondayAfter(Date.of(2015,5,23)))
    assert( Date.of(2015,6,1) == secondMondayAfter(Date.of(2015,5,24)))
    assert( Date.of(2015,6,8) == secondMondayAfter(Date.of(2015,5,25)))
    assert( Date.of(2015,6,8) == secondMondayAfter(Date.of(2015,5,26)))
    println(s"second monday after $anchor is ${secondMondayAfter(anchor)}")

    val fridayBefore : Seek = parseAll(seek, "friday before").get
    assert( Date.of(2015,5,15) == fridayBefore(Date.of(2015,5,21)) )
    assert( Date.of(2015,5,15) == fridayBefore(Date.of(2015,5,22)), fridayBefore(Date.of(2015,5,22)) )
    assert( Date.of(2015,5,22) == fridayBefore(Date.of(2015,5,23)) )
    println(s"friday before $anchor is ${fridayBefore(anchor)}")

    val threeMonthsAfter = parseAll(seek2, "three months after").get.shift _
    assert( Date.of(2015,8,22) isEqual threeMonthsAfter(Date.of(2015,5,22)), threeMonthsAfter(Date.of(2015,5,22)) )
    assert( Date.of(2015,8,30) isEqual threeMonthsAfter(Date.of(2015,5,30)) )
    assert( Date.of(2015,2,28) isEqual threeMonthsAfter(Date.of(2014,11,30)) )

    val everySecondDay = parseAll(stream, "every second day").get.apply _
    assert( Date.of(2015,5,22) isEqual everySecondDay(Date.of(2015,5,22))(0) , everySecondDay(Date.of(2015,5,22))(0) )
    assert( Date.of(2015,5,24) isEqual everySecondDay(Date.of(2015,5,22))(1) , everySecondDay(Date.of(2015,5,22))(1) )
    assert( Date.of(2015,6,1)  isEqual everySecondDay(Date.of(2015,5,22))(5) , everySecondDay(Date.of(2015,5,22))(5) )

    //val nextThirdWednesdayInQarter = parseAll(?, "next third wednesday in quarter")

    //val thirdWednesdayInNextQuarter = parseAll(?, "third wednesday in next quarter")
  }
}


