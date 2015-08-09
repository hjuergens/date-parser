package de.juergens.text

import java.time._
import java.time.format.DateTimeFormatter
import java.time.temporal._
import java.util.{Locale, Objects}
import de.juergens.time._
import de.juergens.time.impl.{YearShifter, DayShifter, TimeUnitShifter}
import de.juergens.util._


import xuwei_k.Scala2Java8._

import scala.util.parsing.combinator._

import java.time.LocalDate
import java.util.function.{Predicate => JPredicate}

object DayOfWeekFormat {
  def apply(pattern:String) = {
    /** is immutable and is thread-safe */
    val formatter1 = DateTimeFormatter.ofPattern(pattern, Locale.US)

    (str:String) => DayOfWeek.of( formatter1.parse(str.capitalize).get(ChronoField.DAY_OF_WEEK) )
  }
}

/**
 *  M/L     month-of-year               number/text       7; 07; Jul; July; J
 */
object MonthFormat {
  def apply(pattern:String) = {
    /** is immutable and is thread-safe */
    val formatter = DateTimeFormatter.ofPattern(pattern, Locale.US)

    (str:String) => {
      java.time.Month.of( formatter.parse(str.capitalize).get(ChronoField.MONTH_OF_YEAR) )
    }
  }
}

/**
 * Q/q     quarter-of-year             number/text       3; 03; Q3; 3rd quarter
 */
object QuarterOfYearFormat {
  /** is immutable and is thread-safe */
  val formatter = DateTimeFormatter.ofPattern("Q", Locale.US)

  // "q"
  def apply(str: String) = ???
}

class Attribute(accessor: TemporalAccessor) extends JPredicate[LocalDate] {
  override def toString = s"Attribute(temporalAccessor=$accessor)"

  override def test(t: LocalDate): Boolean = {
    val i : TemporalQuery[LocalDate] = TemporalQueries.localDate()
    val r : LocalDate = i.queryFrom(accessor)  //temporal = temporal.query(thisQuery);
    r equals t
  }
}

class IsMonday extends TemporalQuery[Boolean] {
  override def queryFrom(temporal: TemporalAccessor): Boolean = {
    //    val date : LocalDate = LocalDate.from(temporal)
    //    MonthDay juneFirst = MonthDay.of(Month.JUNE.getValue(), 1);
    DayOfWeek.from(temporal) == DayOfWeek.MONDAY
  }
}


/**
 * Created by juergens on 27.05.15.
 */
class DateRuleParser extends JavaTokenParsers with NumberParser with ExtendedRegexParsers {
  /**
   * implicit transformation
   * @param func Temporal to Temporal
   * @return a TemporalAdjuster
   */
  implicit private class ThisTemporalAdjuster(func: (Temporal)=>Temporal) extends TemporalAdjuster {
    override def adjustInto(temporal: Temporal): Temporal = func(temporal)
  }

  def shift: Parser[(Sign,Int)] = ("+" | "-")~wholeNumber ^^ {x=>(Sign(x._1),x._2.toInt)}
  def number: Parser[Int] = wholeNumber ^^ {x=>x.toInt} | "" ^^ {_=>0.intValue()}

  def dayOfWeek : Parser[DayOfWeek] = ("monday" | "tuesday" |"wednesday"|"thursday"|"friday"|"saturday" |"sunday") ^^ { DayOfWeekFormat("EEEE") }
  //  def weekDayPredicate : Parser[JPredicate[LocalDate]] = ("monday"|"tuesday"|"wednesday"|"thursday"|"friday"|"saturyday"|"sunday") ^^ { str => WeekDayPredicate(WeekDay(str)) }
  //def month : Parser[JPredicate[LocalDate]] = ("Jan" | "Feb" | "Mar" | "march"| "Apr" | "april" | "May" | "Jun" | "june" | "Jul" | "Aug" | "Sep" | "september" | "Oct" | "Nov" | "Dec" | "december") ^^ {Month(_)}
  private def month3 : Parser[java.time.Month] = ("Jan" | "Feb" | "Mar" | "Apr" |"May" | "Jun" | "Jul" | "Aug" | "Sep" | "Oct" | "Nov" | "Dec" ) ^^ { MonthFormat("MMM")(_) }
  private def monthLong : Parser[java.time.Month] = ("march"| "april" | "may" | "june" | "august" | "september" | "december") ^^ { MonthFormat("MMMM")(_)}
  def monthName : Parser[java.time.Month] = month3 | monthLong

  /** PnYnMnD */
  def unit : Parser[TemporalUnit] = ("year" | "quarter" | "month" | "week" | "day") ^^ {str => ChronoUnit.valueOf(str+"s")}

  def year : Parser[java.time.Year] = nextPrevious ~ "year" ^^ { x => Year.now() }
  // IsoFields.QUARTER_OF_YEAR
  def quarter : Parser[java.time.Month] = ("first"|"second"|"third"|"fourth") ~  "quarter" ^^ {
    case ~("first",_) => java.time.Month.JANUARY
    case ~("second",_) => java.time.Month.APRIL
    case ~("third",_) => java.time.Month.JULY
    case ~("fourth",_) => java.time.Month.OCTOBER
  }

  def temporalAccessor : Parser[TemporalAccessor] = dayOfWeek | monthName | year | quarter

  def attribute : Parser[JPredicate[LocalDate]] = temporalAccessor ^^ { (accessor: TemporalAccessor)  => new JPredicate[LocalDate] {
    override def toString = s"JPredicate(temporalAccessor=$accessor)"

    object Filter extends TemporalQuery[Boolean] {
      def matchAttribute(t: TemporalAccessor) =     accessor match {
        case dayOfWeek : DayOfWeek => DayOfWeek.from(t) == dayOfWeek
      }
      override def queryFrom(temporal: TemporalAccessor) = matchAttribute(temporal)
    }
    override def test(t: LocalDate): Boolean = {
      t.query(Filter)
    }
  }}

  def timeUnitSingular : Parser[TemporalUnit] = ("year" | "quarter" | "month"| "week" | "day") ^^ {TimeUnit(_)}

  def timeUnit : Parser[TemporalUnit] = ("years" | "quarters" | "months"| "weeks" | "days") ^^ {TimeUnit(_)}

  // next to last  (als) vorletzter
  // next previous last first
  def nextPrevious : Parser[Direction] = ("next" | "previous" ) ^^ { Direction(_) }
  def afterObefore : Parser[Direction] = ( "after" | "before" ) ^^ { Direction(_) }
  def direction : Parser[Direction] = afterObefore | nextPrevious

  /** e.g. second friday after */
  def seekWeekDay : Parser[TemporalAdjuster] =  ordinal ?~ ( dayOfWeek ~ direction ) ^^
    { triple => DayOfWeekAdjuster(triple._1.getOrElse(Ordinal(1)), triple._2._1, triple._2._2) }

  def seekMonth :   Parser[TemporalAdjuster] = ordinal ?~ (monthName ~ direction) ^^
    { triple => MonthAdjuster(triple._1.getOrElse(Ordinal(1)), triple._2._1,triple._2._2) }

  /** e.g. three months */
  def periodUnit : Parser[TemporalAmount] = cardinal~timeUnit ^^
    { pair => de.juergens.time.Period.of(pair._1.toInt, pair._2) }
  def period : Parser[TemporalAmount] = repsep(periodUnit, ("and" | ",")) ^^
    { _.foldRight(java.time.Period.ZERO)( (amount:TemporalAmount,period:java.time.Period) => period.plus(amount))}

  /** e.g. three months before */
  def seek2 : Parser[TimeUnitShifter] = cardinal~timeUnit~direction ^^
    { triple => TimeUnitShifter(triple._2 * triple._1._1.toInt, triple._1._2) }


  def stream : Parser[LocalDate => Stream[LocalDate]] = "every"~ordinalTimeUnit ^^
    { x=> (date:LocalDate) => Stream.iterate(date)(x._2.shift) }

  // next week, next month, next friday, next april, next spring    ordinal: Ordinal, dayOfWeek: DayOfWeek, direction: Direction
  def nextPreviousUnit : Parser[TemporalAdjuster] = nextPrevious ~ timeUnitSingular ^^
    { x=> TemporalPeriodSeek(Ordinal(1), x._2, x._1) }

  def nextPreviousName : Parser[TemporalAdjuster] = nextPrevious ~ (dayOfWeek | monthName) ^^
    {
      case ~(direction, w:DayOfWeek) => DayOfWeekAdjuster(Ordinal(1), w, direction)
      case ~(direction, m:java.time.Month) => MonthAdjuster(Ordinal(1), m, direction)
    }

  def ordinalTimeUnit : Parser[TimeUnitShifter] = ordinal~timeUnitSingular ^^
    { x=> TimeUnitShifter(x._1.toInt, x._2) }

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

  def adjuster : Parser[TemporalAdjuster] =
    tomorrowYesterdayToday | seekWeekDay | seekMonth | nextPreviousUnit | nextPreviousName

  def adjuster_ : Parser[TemporalAdjuster] = ("last"|"first") ~ dayOfWeek ~ "in" ~ monthName ^^
    {
      (x) => new TemporalAdjuster {
        val ~(~(~(lastOrFirst,dayOfWeek),_),month) = x

        override def adjustInto(temporal: Temporal): Temporal = {
          temporal.ensuring(_.isSupported(ChronoField.DAY_OF_WEEK))
          temporal.ensuring(_.isSupported(ChronoField.MONTH_OF_YEAR))
          temporal.`with`(month).`with`(lastOrFirst match {
            case "first" => TemporalAdjusters.firstInMonth(dayOfWeek)
            case "last" => TemporalAdjusters.lastInMonth(dayOfWeek)
          })
        }
      }
    }

  def today : Parser[TemporalAdjuster] =  "today" ^^ { _ => TemporalAdjusters.ofDateAdjuster((ld:LocalDate)=>ld) }
  def yesterday :Parser[DayShifter] = ("yesterday" | "day before").+  ^^ { x => DayShifter(-x.length) }
  def tomorrow  : Parser[DayShifter] = ("tomorrow" | "day after").+   ^^ { x => DayShifter(x.length) }

  def tomorrowYesterdayToday : Parser[TemporalAdjuster] = tomorrow | yesterday | today

  def rule : Parser[Temporal => Stream[Temporal]] = """.*""".r ^^ { _ => (t:Temporal) => Stream.empty[Temporal]}

  def interval : Parser[org.threeten.extra.Interval] = "in" ~ monthName ^^ { (x) => TemporalInterval.fromMonth(x._2) }
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
    val theDayBefore = parseAll(adjuster, "the day before").get.adjustInto _

    assert( theDayBefore(today) == today.minusDays(1))
    assert( theDayBefore(today) ==  Duration.ofDays(1).subtractFrom(today))
  }

}


