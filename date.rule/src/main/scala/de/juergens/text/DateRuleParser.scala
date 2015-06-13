package de.juergens.text

import java.time._
import java.time.format.DateTimeFormatter
import java.time.temporal._
import java.util.{Locale, Objects}
import java.util.function.UnaryOperator

import de.juergens.rule.Predicate
import de.juergens.time.{TimeUnit, _}
import de.juergens.time.impl.{DayShifter, TimeUnitShifter}
import de.juergens.util._
import xuwei_k.Scala2Java8._

import scala.util.parsing.combinator._

import java.time.LocalDate
import java.util.function.{Predicate => JPredicate}

object DayOfWeekFormat {
  def apply(pattern:String) = {
    /** is immutable and is thread-safe */
    val formatter1 = DateTimeFormatter.ofPattern(pattern, Locale.US)

    println(formatter1.format(LocalDate.of(2015,5,31)))
    println(DateTimeFormatter.ofPattern("EE", Locale.US).format(LocalDate.of(2015,5,31)))
    println(DateTimeFormatter.ofPattern("EEE", Locale.US).format(LocalDate.of(2015,5,31)))
    println(DateTimeFormatter.ofPattern("EEEE", Locale.US).format(LocalDate.of(2015,5,31)))
    println(DateTimeFormatter.ofPattern("EEEEE", Locale.US).format(LocalDate.of(2015,5,31)))

    (str:String) => DayOfWeek.of( formatter1.parse(str.capitalize).get(ChronoField.DAY_OF_WEEK) )
  }
}

/**
 *  M/L     month-of-year               number/text       7; 07; Jul; July; J
 */
object MonthFormat {
  def apply(pattern:String) = {
    /** is immutable and is thread-safe */
    val formatter = DateTimeFormatter.ofPattern(pattern)
    (str:String) => {
        java.time.Month.of( formatter.parse(str).get(ChronoField.MONTH_OF_YEAR) )
    }
  }
}

/**
 * Q/q     quarter-of-year             number/text       3; 03; Q3; 3rd quarter
 */
object QuarterOfYearFormat {
  /** is immutable and is thread-safe */
  val formatter = DateTimeFormatter.ofPattern("Q")

  // "q"
  def apply(str: String) = ???
}

/**
 * Created by juergens on 27.05.15.
 */
class DateRuleParser extends JavaTokenParsers {
  implicit def func2TemporalAdjuster(func: (Temporal)=>Temporal) = new TemporalAdjuster{
    override def adjustInto(temporal: Temporal): Temporal = func(temporal)
  }

  val SignNumber = """(\+|-)?(\d+)""".r
  def shift: Parser[(Sign,Int)] = ("+" | "-")~wholeNumber ^^ {x=>(Sign(x._1),x._2.toInt)}
  def number: Parser[Int] = wholeNumber ^^ {x=>x.toInt} | "" ^^ {_=>0.intValue()}

  def dayOfWeek : Parser[DayOfWeek] = ("monday"|"tuesday"|"wednesday"|"thursday"|"friday"|"saturyday"|"sunday") ^^ { str => DayOfWeekFormat("EEEE")(str) }
//  def weekDayPredicate : Parser[JPredicate[LocalDate]] = ("monday"|"tuesday"|"wednesday"|"thursday"|"friday"|"saturyday"|"sunday") ^^ { str => WeekDayPredicate(WeekDay(str)) }
//def month : Parser[JPredicate[LocalDate]] = ("Jan" | "Feb" | "Mar" | "march"| "Apr" | "april" | "May" | "Jun" | "june" | "Jul" | "Aug" | "Sep" | "september" | "Oct" | "Nov" | "Dec" | "december") ^^ {Month(_)}
  def month3 : Parser[java.time.Month] = ("Jan" | "Feb" | "Mar" | "Apr" |"May" | "Jun" | "Jul" | "Aug" | "Sep" | "Oct" | "Nov" | "Dec" ) ^^ { MonthFormat("LLL")(_) }
  def monthLong : Parser[java.time.Month] = ("march"| "april" | "june" | "september" | "december") ^^ {MonthFormat("L")(_)}
  def month : Parser[java.time.Month] = month3 | monthLong

  def temporalAccessor : Parser[TemporalAccessor] = dayOfWeek | month

  def attribute : Parser[JPredicate[LocalDate]] = temporalAccessor ^^ { (x: TemporalAccessor)  => new JPredicate[LocalDate] {
    override def test(t: LocalDate): Boolean = {
      val i : TemporalQuery[LocalDate] = TemporalQueries.localDate()
      val r : LocalDate = i.queryFrom(x)  //temporal = temporal.query(thisQuery);
      r equals t
    }
  }}

  def timeUnitSingular : Parser[TimeUnit] = ("year" | "quarter" | "month"| "week" | "day") ^^ {TimeUnit(_)}

  def timeUnit : Parser[TimeUnit] = ("years" | "quarters" | "months"| "weeks" | "days") ^^ {TimeUnit(_)}

  def ordinal : Parser[Ordinal] = ("first" | "second" | "third") ^^ { Ordinal.fromString(_) }
  def cardinal : Parser[Cardinal] = ("one" | "two" | "three") ^^ { Cardinal.fromString(_) }

  def nextPrevious : Parser[Direction] = ("next" | "previous" ) ^^ { Direction(_) }
  def direction1 : Parser[Direction] = ( "after" | "before" ) ^^ { Direction(_) }
  def direction : Parser[Direction] = direction1 | nextPrevious

  /** e.g. second friday after */
  def seekWeekDay : Parser[TemporalAdjuster] = ordinal~dayOfWeek~direction ^^ { triple => WeekDaySeek(triple._1._1,triple._1._2,triple._2) } | dayOfWeek~direction ^^ { triple => WeekDaySeek(Ordinal(1), triple._1,triple._2) }
  def seekMonth :   Parser[TemporalAdjuster] = ordinal~month~direction ^^ { triple => MonthSeek(triple._1._1,triple._1._2,triple._2) } | month~direction ^^ { triple => MonthSeek(Ordinal(1), triple._1,triple._2) }

  /** e.g. three months before */
  def seek2 : Parser[TimeUnitShifter] = cardinal~timeUnit~direction ^^ { triple => TimeUnitShifter(triple._2 * triple._1._1.toInt, triple._1._2) }


  def stream : Parser[LocalDate => Stream[LocalDate]] = "every"~ordinalTimeUnit ^^ { x=> (date:LocalDate) => Stream.iterate(date)(x._2.shift) }

  // next week, next month, next friday, next april, next spring    ordinal: Ordinal, dayOfWeek: DayOfWeek, direction: Direction
  def unknown22      : Parser[TemporalAdjuster] = nextPrevious~dayOfWeek ^^  { x=> WeekDaySeek(Ordinal(1), x._2, Up) }
  def unknown        : Parser[TimeUnitShifter] = direction~timeUnitSingular ^^  { x=> TimeUnitShifter(x._1.*(1), x._2) }
  def ordinalTimeUnit : Parser[TimeUnitShifter] = ordinal~timeUnitSingular ^^ { x=> TimeUnitShifter(x._1.toInt, x._2) }

  def dayOfWeekAdjuster : Parser[TemporalAdjuster] = ("the" | cardinal)~dayOfWeek~direction ^^ {u => (x:Temporal)=>x} // TODO implement
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

  def adjuster : Parser[TemporalAdjuster] = adjusterPrimitive | seekWeekDay | unknown22

//  def concatenation : Parser[TemporalAdjuster] = tomorrow~tomorrow.  ^^ { pair => Concatenation( pair._1,pair._2 ) }

  def adjusterPrimitive : Parser[TemporalAdjuster] = tomorrow | yesterday | today

  def today : Parser[TemporalAdjuster] =  ("""[T,t]+oday""".r) ^^ { _ => TemporalAdjusters.ofDateAdjuster((ld:LocalDate)=>ld) }
  def yesterday :Parser[DayShifter] = ("yesterday" | "Yesterday" | "day before").+  ^^ { x => DayShifter(-x.length) }
  def tomorrow  : Parser[DayShifter] = ("tomorrow" | "Tomorrow" | "day after").+   ^^ { x => DayShifter(x.length) }

//  def tomorrow2  : Parser[DayShifter] = tomorrow.*  ^^ {  x =>  DayShifter(x.length) }
}

object Concatenation {
  def apply(lhsTa:TemporalAdjuster, rhsTa:TemporalAdjuster) =
    new TemporalAdjuster{
      override def adjustInto(temporal: Temporal): Temporal = lhsTa.adjustInto( rhsTa.adjustInto(temporal) )
    }
  
}

object ParseExpr$Date extends DateRuleParser {
  def main(args: Array[String]) {
    test()
    //       println("input : " + args(0))
    //       println(parseAll(expr, args(0)))
  }

  /*
    * Best practice for applications is to pass a {@code Clock} into any method
    * that requires the current instant. A dependency injection framework is one
    * way to achieve this:
    */
  private val clock : Clock = Clock.system(ZoneId.systemDefault())  // dependency inject
  import java.time.temporal.TemporalAdjusters._

  val timePoint : LocalDateTime = LocalDateTime.now(clock)
  Objects.requireNonNull(timePoint, "timePoint")
  val foo = timePoint.`with`(lastDayOfMonth())
  val bar = timePoint.`with`(previousOrSame(DayOfWeek.WEDNESDAY))

  // Using value classes as adjusters
  timePoint.`with`(LocalTime.now())

  def test(): Unit = {
    val today = LocalDateTime.now(clock)
    val theDayBefore = parseAll(dayOfWeekAdjuster, "the day before").get.adjustInto _

    assert( theDayBefore(today) == today.minusDays(1))
    assert( theDayBefore(today) ==  Duration.ofDays(1).subtractFrom(today))
  }

}


