package de.juergens

//import de.juergens.{DateII=>Date}
//import de.juergens.Date
import de.juergens.time.{Calendar, Date => _}
import scala.util.parsing.input.CharSequenceReader
//import de.digitec.p2.finance.{BusinessRule, MaturityAlternative, Calendar, Date}
import java.io.Serializable
import de.juergens.time.{EnrichedDate => Date}

/**
 * @param predicate The Condition the DateShifter tries to meet by increasing or decreasing input dates.
 */
class DateShifter(predicate: Date => Boolean) extends Serializable {

   /**
    * helper method for moving dates until a condition is true
    */
   private def moveDateUntil(anOffset: Int)(aDate: Date): Date = {
      var date = aDate
      while (!predicate(date))
         date += anOffset
      date
   }

   /**
    * move date forward until condition is met
    */
   def inc(date: Date): Date = {
      moveDateUntil(1)(date)
   }

   /**
    * move date backward until condition is met
    */
   def dec(date: Date): Date = {
      moveDateUntil(-1)(date)
   }

   /**
    * skip forward to next date that meets the condition
    * @param minOffset allows optimization by skipping several days at once (used for weekday shifters)
    */
   def next(date: Date, minOffset: Int = 1): Date = {
      if (predicate(date))
         inc(date + minOffset)
      else
         inc(date)
   }

   /**
    * skip backward to previous date that meets the condition
    */
   def previous(date: Date, minOffset: Int = 1): Date = {
      if (predicate(date))
         dec(date - minOffset)
      else
         dec(date)
   }
}

sealed abstract class Sign {
   def *(i: Int): Int

   override final def toString: String = this match {
      case Sign(name) => name
   }
}

object Plus extends Sign {
   def *(i: Int) = i
}

object Minus extends Sign {
   def *(i: Int) = -i
}

object Sign {
   def apply(str: String): Sign = str match {
      case "+" | "plus" => Plus
      case "-" | "minus" => Minus
   }

   def unapply(unit: Sign): Option[String] = PartialFunction.condOpt(unit) {
      case Plus => "+"
      case Minus => "-"
   }

}



   trait Predicate[T] {
      def evaluate(t: T): Boolean
   }


   abstract class DateRule {
      def evaluate(anchor: Date, t: Date): Boolean

      def date(anchor: Date, calendar: Calendar): Date = {
         def predicate(t: Date) = evaluate(anchor, t)
         val shifter = new DateShifter(predicate)
         shifter.next(anchor)
      }
   }

   case class UnionRule(rules: List[DateRule]) extends DateRule {
      def evaluate(anchor: Date, t: Date): Boolean = rules.foldLeft(false)((b, r) => b | r.evaluate(anchor, t))

      override def toString = rules.mkString("|")
   }

   case class IntersectionRule(rules: List[DateRule]) extends DateRule {
      def evaluate(anchor: Date, t: Date): Boolean = rules.foldRight(true)((r, b) => b & r.evaluate(anchor, t))

      override def toString = rules.mkString("&")
   }

   case class ListRule(rules: List[DateRule]) extends DateRule {
      def evaluate(anchor: Date, t: Date): Boolean = {
         t == rules.foldLeft(anchor)((d, s) => s.date(d, null))
      }

      override def toString = rules.mkString("(", ">", ")")
   }

   case class ShiftRule(shifter: Shifter) extends DateRule {
      def evaluate(anchor: Date, t: Date): Boolean = {
         val date = shifter.shift(anchor)
         t == date
      }

      override def toString = shifter.toString
   }

   case class FixRule(number: Int, component: DateComponent) extends DateRule {
      def evaluate(anchor: Date, t: Date): Boolean = {
         t == component
      }

      override def toString = number + " " + component.toString
   }

   abstract sealed class TimeUnit extends DateComponent {
      override final def toString: String = this match {
         case TimeUnit(name) => name
      }
   }

   case object DayUnit extends TimeUnit {
      def apply(offset: Int)(date: Date) = {
         val number = DateComponent.toNumber(date) + offset
         DateComponent.fromNumber(number)
      }
   }

   case object WeekUnit extends TimeUnit

   case object MonthUnit extends TimeUnit

   case object QuarterUnit extends TimeUnit

   case object YearUnit extends TimeUnit

   object TimeUnit {
      def apply(str: String): TimeUnit = str match {
         case "day" => DayUnit
         case "week" => WeekUnit
         case "month" => MonthUnit
         case "quarter" => QuarterUnit
         case "year" => YearUnit
      }

      def unapply(unit: TimeUnit): Option[String] = PartialFunction.condOpt(unit) {
         case DayUnit => "day"
         case WeekUnit => "week"
         case MonthUnit => "month"
         case QuarterUnit => "quarter"
         case YearUnit => "year"
      }
   }

   abstract sealed class DateComponent

   object DateComponent {
      /**
       * days since 0/1/1
       * @see http://alcor.concordia.ca/~gpkatch/gdate-algorithm.html
       */
      def toNumber(date: Date): Int = {
         val Date(year, month, day) = date
         val m = (month + 9) % 12 /* mar=0, feb=11 */
         val y = year - m / 10 /* if Jan/Feb, year-- */
         y * 365 + y / 4 - y / 100 + y / 400 + (m * 306 + 5) / 10 + (day - 1)
      }

      /**
       * days since 0/1/1  to Date
       * @see http://alcor.concordia.ca/~gpkatch/gdate-algorithm.html
       */
      def fromNumber(g: Long): Date = {
         var y = (10000 * g + 14780) / 3652425
         var ddd = g - (365 * y + y / 4 - y / 100 + y / 400)
         if (ddd < 0) {
            y = y - 1
            ddd = g - (365 * y + y / 4 - y / 100 + y / 400)
         }
         val mi = (100 * ddd + 52) / 3060
         val mm = (mi + 2) % 12 + 1
         y = y + (mi + 2) / 12
         val dd = ddd - (mi * 306 + 5) / 10 + 1
         Date(y.intValue, mm.intValue, dd.intValue)
      }

      /**
       * @param year four-digit
       * @return true only if year is a leap year
       */
      def isLeap(year: Int) = {
         ((year % 4 == 0) && (year % 100 != 0)) || (year % 400 == 0)
      }
   }

   abstract class Month extends NamedDateComponent {
      override final def toString: String = this match {
         case Month(name) => name
      }
   }

   abstract class Shifter {
      def shift(t: Date): Date
   }

   case class WeekShifter(step: Int) extends Shifter {
      def shift(t: Date) = t + step * 7

      override def toString = ("%+d".format(step)) + " " + WeekUnit.toString
   }

   case class DayShifter(step: Int) extends Shifter {
      def shift(t: Date) = t + step * 1

      override def toString = ("%+d".format(step)) + " " + DayUnit.toString
   }

   case class QuarterShifter(aStep: Int) extends Shifter {
      private val monthShifter = MonthShifter(3 * aStep)

      def shift(t: Date) = monthShifter.shift(t)

      override def toString = ("%+d".format(aStep)) + " " + QuarterUnit.toString
   }

   case class YearShifter(step: Int) extends Shifter {
      def shift(t: Date) = {
         val Date(year, month, day) = t
         val newYear = year + step
         val shiftedDay = day min Month.daysIn(Month(month), newYear)
         Date(year + step, month, shiftedDay)
      }

      override def toString = ("%+d".format(step)) + " " + YearUnit.toString
   }

   case class MonthShifter(step: Int) extends Shifter {
      def shift(t: Date) = {
         var newDay = t.day
         var newMonth = t.month + step
         var newYear = t.year

         // tritt nur bei Subtraktion auf
         if (newMonth < 1)
            newYear += ((newMonth / 12) - 1) // DIV 12

         // tritt nur bei Addition auf
         if (newMonth > 12)
            newYear += ((newMonth - 1) / 12) // DIV 12

         newMonth %= 12 // MOD 12

         // Die Berechnung newMonth % 12 kann 0 ergeben. Damit muss zumindest der
         // Fall newMonth == 0 abgefangen werden!
         if (newMonth < 1)
            newMonth += 12

         if (!Date.isValid(newYear, newMonth, newDay)) {
            newDay = Date.daysIn(newYear, newMonth)
         }
         Date(newYear, newMonth, newDay)
      }

      override def toString = ("%+d".format(step)) + " " + MonthUnit.toString
   }

   class DateComponentShifter(step: Int, dateComponent: NamedDateComponent) extends Shifter {
      def shift(t: Date) = {throw new UnsupportedOperationException()}

      override def toString = ("%+d".format(step)) + " " + dateComponent.toString
   }

   object DateComponentShifter {
      def apply(step: Int, dateComponent: DateComponent): Shifter = dateComponent match {
         case DayUnit => DayShifter(step)
         case WeekUnit => WeekShifter(step)
         case MonthUnit => MonthShifter(step)
         case QuarterUnit => QuarterShifter(step)
         case YearUnit => YearShifter(step)
         case dc : NamedDateComponent => new DateComponentShifter(step, dc)
      }

      def unapply(shifter: Shifter): Option[(Int, DateComponent)] = PartialFunction.condOpt(shifter) {
         case DayShifter(step) => (step, DayUnit)
         case WeekShifter(step) => (step, WeekUnit)
         case YearShifter(step) => (step, YearUnit)
      }
   }

   case object Jan /*(year:Year)*/ extends Month

   case object Feb /*(year:Year)*/ extends Month

   case object Mar /*(year:Year)*/ extends Month

   case object Apr /*(year:Year)*/ extends Month

   case object May /*(year:Year)*/ extends Month

   case object Jun /*(year:Year)*/ extends Month

   case object Jul /*(year:Year)*/ extends Month

   case object Aug /*(year:Year)*/ extends Month

   case object Sep /*(year:Year)*/ extends Month

   case object Oct /*(year:Year)*/ extends Month

   case object Nov /*(year:Year)*/ extends Month

   case object Dec /*(year:Year)*/ extends Month

   object Month {
      def apply(str: String): Month = str match {
         case "Jan" | "januar" => Jan
         case "Feb" | "february" => Feb
         case "Mar" | "march" => Mar
         case "Apr" | "april" => Apr
         case "May" | "may" => May
         case "Jun" | "june" => Jun
         case "Jul" => Jul
         case "Aug" => Aug
         case "Sep" | "september" => Sep
         case "Oct" => Oct
         case "Nov" => Nov
         case "Dec" | "december" => Dec
      }
      def apply(number: Int): Month = number match {
         case 1 => Jan
         case 2 => Feb
         case 3 => Mar
         case 4 => Apr
         case 5 => May
         case 6 => Jun
         case 7 => Jul
         case 8 => Aug
         case 9 => Sep
         case 10 => Oct
         case 11 => Nov
         case 12 => Dec
      }

      def unapply(month: Month): Option[String] = PartialFunction.condOpt(month) {
         case Jan => "Jan"
         case Feb => "Feb"
         case Mar => "Mar"
         case Apr => "Apr"
         case May => "May"
         case Jun => "Jun"
         case Jul => "Jul"
         case Aug => "Aug"
         case Sep => "Sep"
         case Oct => "Oct"
         case Nov => "Nov"
         case Dec => "Dec"
      }

      def daysIn(month:Month, aYear: Int): Int = {
         month match {
            case Jan => 31
            case Feb => if(Date.isLeap(aYear)) 29 else 28
            case Mar => 31
            case Apr => 30
            case May => 31
            case Jun => 30
            case Jul => 31
            case Aug => 31
            case Sep => 30
            case Oct => 31
            case Nov => 30
            case Dec => 31
         }
      }
   }

   abstract class NamedDateComponent extends DateComponent

   abstract class WeekDay extends NamedDateComponent {
      override final def toString: String = this match {
         case WeekDay(name) => name
      }
   }

   case object Monday extends WeekDay

   case object Tuesday extends WeekDay

   case object Wednesday extends WeekDay

   case object Thursday extends WeekDay

   case object Friday extends WeekDay

   case object Saturday extends WeekDay

   case object Sunday extends WeekDay

   case class Day(day: Int) extends DateComponent

   object WeekDay {

      def apply(str: String): WeekDay = str match {
         case "monday" => Monday
         case "tuesday" => Tuesday
         case "wednesday" => Wednesday
         case "thursday" => Thursday
         case "friday" => Friday
         case "saturday" => Saturday
         case "sunday" => Sunday
      }

      def unapply(weekDay: WeekDay): Option[String] = PartialFunction.condOpt(weekDay) {
         case Monday => "monday"
         case Tuesday => "tuesday"
         case Wednesday => "wednesday"
         case Thursday => "thursday"
         case Friday => "friday"
         case Saturday => "saturday"
         case Sunday => "sunday"
      }
   }

   // "year + 1 > month 3 > day 14"
   // "wednesday -1"
   import scala.util.parsing.combinator._
   class RuleParser extends JavaTokenParsers {
      def expr: Parser[DateRule] = repsep(term, "|") ^^ {y => new UnionRule(y)}
      def term: Parser[DateRule] = repsep(factor, "&") ^^ {x => new IntersectionRule(x)}
      def factor: Parser[DateRule] = unitRule^^{x=>x} | "("~>repsep(expr, ">")<~")" ^^ {z => new ListRule(z)}  | repsep(expr, ">") ^^ {z => new ListRule(z)}

      def shift: Parser[(Sign,Int)] = ("+" | "-")~wholeNumber ^^ {x=>(Sign(x._1),x._2.toInt)}
      def number: Parser[Int] = wholeNumber ^^ {x=>x.toInt} | "" ^^ {_=>0.intValue()}

      def weekDay : Parser[WeekDay] = ("monday"|"tuesday"|"wednesday"|"thursday"|"friday"|"saturyday"|"sunday") ^^ {WeekDay(_)}
      def month : Parser[Month] = ("Jan" | "Feb" | "Mar" | "march"| "Apr" | "april" | "May" | "Jun" | "june" | "Jul" | "Aug" | "Sep" | "september" | "Oct" | "Nov" | "Dec" | "december") ^^ {Month(_)}
      def timeUnit : Parser[TimeUnit] = ("year" | "quarter" | "month" | "week" | "day") ^^ {TimeUnit(_)}

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



class RuleParser2 {

   trait Predicate[T] {
      def evaluate(t: T): Boolean
   }


   abstract class BaseRule {
      def evaluate(anchor: Date, t: Date): Boolean

      def date(anchor: Date, calendar: Calendar): Date = {
         def predicate(t: Date) = evaluate(anchor, t)
         val shifter = new DateShifter(predicate)
         shifter.next(anchor)
      }
   }

   case class UnionRule(rules: List[BaseRule]) extends BaseRule {
      def evaluate(anchor: Date, t: Date): Boolean = rules.foldLeft(false)((b, r) => b | r.evaluate(anchor, t))

      override def toString = rules.mkString("|")
   }

   case class IntersectionRule(rules: List[BaseRule]) extends BaseRule {
      def evaluate(anchor: Date, t: Date): Boolean = rules.foldRight(true)((r, b) => b & r.evaluate(anchor, t))

      override def toString = rules.mkString("&")
   }

   case class ListRule(rules: List[BaseRule]) extends BaseRule {
      def evaluate(anchor: Date, t: Date): Boolean = {
         t == rules.foldLeft(anchor)((d, s) => s.date(d, null))
      }

      override def toString = rules.mkString("(", ">", ")")
   }

   case class ShiftRule(shifter: Shifter) extends BaseRule {
      def evaluate(anchor: Date, t: Date): Boolean = {
         val date = shifter.shift(anchor)
         t == date
      }

      override def toString = shifter.toString
   }

   case class FixRule(number: Int, component: DateComponent) extends BaseRule {
      def evaluate(anchor: Date, t: Date): Boolean = {
         t == component
      }

      override def toString = number + " " + component.toString
   }

   abstract sealed class TimeUnit extends DateComponent {
      override final def toString: String = this match {
         case TimeUnit(name) => name
      }
   }

   case object DayUnit extends TimeUnit {
      def apply(offset: Int)(date: Date) = {
         val number = DateComponent.toNumber(date) + offset
         DateComponent.fromNumber(number)
      }
   }

   case object WeekUnit extends TimeUnit

   case object MonthUnit extends TimeUnit

   case object QuarterUnit extends TimeUnit

   case object YearUnit extends TimeUnit

   object TimeUnit {
      def apply(str: String): TimeUnit = str match {
         case "day" => DayUnit
         case "week" => WeekUnit
         case "month" => MonthUnit
         case "quarter" => QuarterUnit
         case "year" => YearUnit
      }

      def unapply(unit: TimeUnit): Option[String] = PartialFunction.condOpt(unit) {
         case DayUnit => "day"
         case WeekUnit => "week"
         case MonthUnit => "month"
         case QuarterUnit => "quarter"
         case YearUnit => "year"
      }
   }

   abstract sealed class DateComponent

   object DateComponent {
      /**
       * days since 0/1/1
       * @see http://alcor.concordia.ca/~gpkatch/gdate-algorithm.html
       */
      def toNumber(date: Date): Int = {
         val Date(year, month, day) = date
         val m = (month + 9) % 12 /* mar=0, feb=11 */
         val y = year - m / 10 /* if Jan/Feb, year-- */
         y * 365 + y / 4 - y / 100 + y / 400 + (m * 306 + 5) / 10 + (day - 1)
      }

      /**
       * days since 0/1/1  to Date
       * @see http://alcor.concordia.ca/~gpkatch/gdate-algorithm.html
       */
      def fromNumber(g: Long): Date = {
         var y = (10000 * g + 14780) / 3652425
         var ddd = g - (365 * y + y / 4 - y / 100 + y / 400)
         if (ddd < 0) {
            y = y - 1
            ddd = g - (365 * y + y / 4 - y / 100 + y / 400)
         }
         val mi = (100 * ddd + 52) / 3060
         val mm = (mi + 2) % 12 + 1
         y = y + (mi + 2) / 12
         val dd = ddd - (mi * 306 + 5) / 10 + 1
         Date(y.intValue, mm.intValue, dd.intValue)
      }

      /**
       * @param year four-digit
       * @return true only if year is a leap year
       */
      def isLeap(year: Int) = {
         ((year % 4 == 0) && (year % 100 != 0)) || (year % 400 == 0)
      }
   }

   abstract class Month extends NamedDateComponent {
      override final def toString: String = this match {
         case Month(name) => name
      }
   }

   abstract class Shifter {
      def shift(t: Date): Date
   }

   case class WeekShifter(step: Int) extends Shifter {
      def shift(t: Date) = t + step * 7

      override def toString = ("%+d".format(step)) + " " + WeekUnit.toString
   }

   case class DayShifter(step: Int) extends Shifter {
      def shift(t: Date) = t + step * 1

      override def toString = ("%+d".format(step)) + " " + DayUnit.toString
   }

   case class QuarterShifter(aStep: Int) extends Shifter {
      private val monthShifter = MonthShifter(3 * aStep)

      def shift(t: Date) = monthShifter.shift(t)

      override def toString = ("%+d".format(aStep)) + " " + QuarterUnit.toString
   }

   case class YearShifter(step: Int) extends Shifter {
      def shift(t: Date) = {
         val Date(year, month, day) = t
         val newYear = year + step
         val shiftedDay = day min Month.daysIn(Month(month), newYear)
         Date(year + step, month, shiftedDay)
      }

      override def toString = ("%+d".format(step)) + " " + YearUnit.toString
   }

   case class MonthShifter(step: Int) extends Shifter {
      def shift(t: Date) = {
         var newDay = t.day
         var newMonth = t.month + step
         var newYear = t.year

         // tritt nur bei Subtraktion auf
         if (newMonth < 1)
            newYear += ((newMonth / 12) - 1) // DIV 12

         // tritt nur bei Addition auf
         if (newMonth > 12)
            newYear += ((newMonth - 1) / 12) // DIV 12

         newMonth %= 12 // MOD 12

         // Die Berechnung newMonth % 12 kann 0 ergeben. Damit muss zumindest der
         // Fall newMonth == 0 abgefangen werden!
         if (newMonth < 1)
            newMonth += 12

         if (!Date.isValid(newYear, newMonth, newDay)) {
            newDay = Date.daysIn(newYear, newMonth)
         }
         Date(newYear, newMonth, newDay)
      }

      override def toString = ("%+d".format(step)) + " " + MonthUnit.toString
   }

   class DateComponentShifter(step: Int, dateComponent: NamedDateComponent) extends Shifter {
      def shift(t: Date) = {throw new UnsupportedOperationException()}

      override def toString = ("%+d".format(step)) + " " + dateComponent.toString
   }

   object DateComponentShifter {
      def apply(step: Int, dateComponent: DateComponent): Shifter = dateComponent match {
         case DayUnit => DayShifter(step)
         case WeekUnit => WeekShifter(step)
         case MonthUnit => MonthShifter(step)
         case QuarterUnit => QuarterShifter(step)
         case YearUnit => YearShifter(step)
         case dc : NamedDateComponent => new DateComponentShifter(step, dc)
      }

      def unapply(shifter: Shifter): Option[(Int, DateComponent)] = PartialFunction.condOpt(shifter) {
         case DayShifter(step) => (step, DayUnit)
         case WeekShifter(step) => (step, WeekUnit)
         case YearShifter(step) => (step, YearUnit)
      }
   }

   case object Jan /*(year:Year)*/ extends Month

   case object Feb /*(year:Year)*/ extends Month

   case object Mar /*(year:Year)*/ extends Month

   case object Apr /*(year:Year)*/ extends Month

   case object May /*(year:Year)*/ extends Month

   case object Jun /*(year:Year)*/ extends Month

   case object Jul /*(year:Year)*/ extends Month

   case object Aug /*(year:Year)*/ extends Month

   case object Sep /*(year:Year)*/ extends Month

   case object Oct /*(year:Year)*/ extends Month

   case object Nov /*(year:Year)*/ extends Month

   case object Dec /*(year:Year)*/ extends Month

   object Month {
      def apply(str: String): Month = str match {
         case "Jan" | "januar" => Jan
         case "Feb" | "february" => Feb
         case "Mar" | "march" => Mar
         case "Apr" | "april" => Apr
         case "May" | "may" => May
         case "Jun" | "june" => Jun
         case "Jul" => Jul
         case "Aug" => Aug
         case "Sep" | "september" => Sep
         case "Oct" => Oct
         case "Nov" => Nov
         case "Dec" | "december" => Dec
      }
      def apply(number: Int): Month = number match {
         case 1 => Jan
         case 2 => Feb
         case 3 => Mar
         case 4 => Apr
         case 5 => May
         case 6 => Jun
         case 7 => Jul
         case 8 => Aug
         case 9 => Sep
         case 10 => Oct
         case 11 => Nov
         case 12 => Dec
      }

      def unapply(month: Month): Option[String] = PartialFunction.condOpt(month) {
         case Jan => "Jan"
         case Feb => "Feb"
         case Mar => "Mar"
         case Apr => "Apr"
         case May => "May"
         case Jun => "Jun"
         case Jul => "Jul"
         case Aug => "Aug"
         case Sep => "Sep"
         case Oct => "Oct"
         case Nov => "Nov"
         case Dec => "Dec"
      }

      def daysIn(month:Month, aYear: Int): Int = {
         month match {
            case Jan => 31
            case Feb => if(Date.isLeap(aYear)) 29 else 28
            case Mar => 31
            case Apr => 30
            case May => 31
            case Jun => 30
            case Jul => 31
            case Aug => 31
            case Sep => 30
            case Oct => 31
            case Nov => 30
            case Dec => 31
         }
      }
   }

   abstract class NamedDateComponent extends DateComponent

   abstract class WeekDay extends NamedDateComponent {
      override final def toString: String = this match {
         case WeekDay(name) => name
      }
   }

   case object Monday extends WeekDay

   case object Tuesday extends WeekDay

   case object Wednesday extends WeekDay

   case object Thursday extends WeekDay

   case object Friday extends WeekDay

   case object Saturday extends WeekDay

   case object Sunday extends WeekDay

   case class Day(day: Int) extends DateComponent

   object WeekDay {

      def apply(str: String): WeekDay = str match {
         case "monday" => Monday
         case "tuesday" => Tuesday
         case "wednesday" => Wednesday
         case "thursday" => Thursday
         case "friday" => Friday
         case "saturday" => Saturday
         case "sunday" => Sunday
      }

      def unapply(weekDay: WeekDay): Option[String] = PartialFunction.condOpt(weekDay) {
         case Monday => "monday"
         case Tuesday => "tuesday"
         case Wednesday => "wednesday"
         case Thursday => "thursday"
         case Friday => "friday"
         case Saturday => "saturday"
         case Sunday => "sunday"
      }
   }

   // "year + 1 > month 3 > day 14"
   // "wednesday -1"

   import scala.util.parsing.combinator._
   class RuleParser extends JavaTokenParsers {
      def expr: Parser[BaseRule] = repsep(term, "|") ^^ {y => new UnionRule(y)}
      def term: Parser[BaseRule] = repsep(factor, "&") ^^ {x => new IntersectionRule(x)}
      def factor: Parser[BaseRule] = unitRule^^{x=>x} | "("~>repsep(expr, ">")<~")" ^^ {z => new ListRule(z)}  | repsep(expr, ">") ^^ {z => new ListRule(z)}

      def shift: Parser[(Sign,Int)] = ("+" | "-")~wholeNumber ^^ {x=>(Sign(x._1),x._2.toInt)}
      def number: Parser[Int] = wholeNumber ^^ {x=>x.toInt} | "" ^^ {_=>0.intValue()}

      def weekDay : Parser[WeekDay] = ("monday"|"tuesday"|"wednesday"|"thursday"|"friday"|"saturyday"|"sunday") ^^ {WeekDay(_)}
      def month : Parser[Month] = ("Jan" | "Feb" | "Mar" | "march"| "Apr" | "april" | "May" | "Jun" | "june" | "Jul" | "Aug" | "Sep" | "september" | "Oct" | "Nov" | "Dec" | "december") ^^ {Month(_)}
      def timeUnit : Parser[TimeUnit] = ("year" | "quarter" | "month" | "week" | "day") ^^ {TimeUnit(_)}

      def dateComponent : Parser[DateComponent] = weekDay | month | timeUnit

//      def shiftRule: Parser[BaseRule] = dateComponent~shift ^^ {x=> DateComponentShifter(x._2._1 * x._2._2,x._1)}// x=>new ShiftRule(Sign(x._2._1),x._2._2, x._1)}
      def dateShifter: Parser[Shifter] = dateComponent~shift ^^ {x=> DateComponentShifter(x._2._1 * x._2._2,x._1)}
      def shiftRule: Parser[BaseRule] = dateShifter ^^ {x=> new ShiftRule(x)}
      def fixRule: Parser[BaseRule] = dateComponent~number ^^ {x=>new FixRule(x._2, x._1)}

      def unitRule : Parser[BaseRule] = shiftRule | fixRule
   }

   val SignNumber = """(\+|-)?(\d+)""".r

   object ParseExpr extends RuleParser {
      def main(args: Array[String]) {
         println("input : " + args(0))
         println(parseAll(expr, args(0)))
      }
   }

}