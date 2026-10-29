package de.juergens.text

import java.time.temporal.{Temporal, TemporalAdjusters, TemporalAmount, TemporalUnit, UnsupportedTemporalTypeException}
import java.time.{DayOfWeek, LocalDate, Period}

import scala.util.parsing.combinator.RegexParsers

/**
 * Shared "schedule" example (see examples/schedule.txt in the project root):
 * {{{
 *   2015-06-09 + 3M, 09.06.2015, (2015-01-15 : 1Q : 2015-12-31)
 * }}}
 */
class ScheduleParsers extends RegexParsers {

  def number: Parser[Int] = """\d+""".r ^^ (_.toInt)

  def date: Parser[LocalDate] = number ~ (
      ("-" ~> number) ~ ("-" ~> number) ^^ { case m ~ d => (y: Int) => LocalDate.of(y, m, d) }  // ISO
    | ("." ~> number) ~ ("." ~> number) ^^ { case m ~ y => (d: Int) => LocalDate.of(y, m, d) }  // German
    | ("/" ~> number) ~ ("/" ~> number) ^^ { case d ~ y => (m: Int) => LocalDate.of(y, m, d) }  // US
    ) ^^ { case first ~ toDate => toDate(first) }

  /** business days, Monday to Friday: only weekends are skipped, there are no holiday calendars */
  case class BusinessDays(count: Int) extends TemporalAmount {
    def get(unit: TemporalUnit): Long = throw new UnsupportedTemporalTypeException(s"business days have no unit: $unit")
    def getUnits: java.util.List[TemporalUnit] = java.util.List.of()
    def addTo(temporal: Temporal): Temporal = plusBusinessDays(LocalDate.from(temporal), count)
    def subtractFrom(temporal: Temporal): Temporal = plusBusinessDays(LocalDate.from(temporal), -count)
  }

  /** the date moved by count business days, backwards if count is negative */
  def plusBusinessDays(date: LocalDate, count: Int): LocalDate =
    if (count == 0) date
    else Iterator.iterate(date)(_.plusDays(count.sign)).drop(1)
      .filter(_.getDayOfWeek.getValue < 6).drop(count.abs - 1).next()

  def period: Parser[TemporalAmount] = number ~ "[DWMQYB]".r ^^ {
    case n ~ "D" => Period.ofDays(n)
    case n ~ "W" => Period.ofWeeks(n)
    case n ~ "M" => Period.ofMonths(n)
    case n ~ "Q" => Period.ofMonths(3 * n)
    case n ~ "Y" => Period.ofYears(n)
    case n ~ "B" => BusinessDays(n)
  }

  /** the third Wednesday of March, June, September or December strictly after the date */
  def nextImm(date: LocalDate): LocalDate =
    Iterator.iterate(date.withDayOfMonth(1))(_.plusMonths(1))
      .filter(_.getMonthValue % 3 == 0)
      .map(_.`with`(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.WEDNESDAY)))
      .find(_.isAfter(date)).get

  /** FX spot: T+1 for USD against CAD, TRY, PHP or RUB, otherwise T+2 */
  def spot(date: LocalDate, pair: Option[String]): LocalDate = {
    val lag = if (pair.exists(_.matches("USD(CAD|TRY|PHP|RUB)|(CAD|TRY|PHP|RUB)USD"))) 1 else 2
    plusBusinessDays(date, lag)
  }

  def pair: Parser[String] = "[A-Z]{6}".r

  def shift: Parser[LocalDate => LocalDate] =
    ("+" ~> period ^^ (p => (d: LocalDate) => d.plus(p))
    | "-" ~> period ^^ (p => (d: LocalDate) => d.minus(p))
    | ">" ~ "IMM" ^^^ (nextImm _)
    | ">" ~ "SPOT" ~> opt(pair) ^^ (p => (d: LocalDate) => spot(d, p)))

  /** shifts are applied from left to right */
  def expr: Parser[LocalDate] = date ~ rep(shift) ^^ {
    case start ~ shifts => shifts.foldLeft(start)((d, f) => f(d))
  }

  def range: Parser[List[LocalDate]] = ("(" ~> expr) ~ (":" ~> period) ~ (":" ~> expr <~ ")") ^^ {
    case first ~ step ~ last =>
      val positive = step match {
        case p: Period => !p.isZero && !p.isNegative
        case BusinessDays(n) => n > 0
      }
      require(positive, s"step must be positive: $step")
      def times(k: Int): TemporalAmount = step match {
        case p: Period => p.multipliedBy(k)
        case BusinessDays(n) => BusinessDays(k * n)
      }
      Iterator.from(0).map(k => first.plus(times(k))).takeWhile(!_.isAfter(last)).toList
  }

  def schedule: Parser[List[LocalDate]] = range | rep1sep(expr, ",")
}

object ScheduleParsers {

  /**
   * @throws IllegalArgumentException on a syntax error or a range without positive step
   * @throws java.time.DateTimeException on an invalid date
   */
  def parse(text: String): List[LocalDate] = {
    val parsers = new ScheduleParsers
    parsers.parseAll(parsers.schedule, text) match {
      case parsers.Success(dates, _) => dates
      case failure: parsers.NoSuccess => throw new IllegalArgumentException(failure.toString)
    }
  }
}
