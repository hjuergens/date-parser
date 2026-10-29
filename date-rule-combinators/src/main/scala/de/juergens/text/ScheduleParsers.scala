package de.juergens.text

import java.time.temporal.{Temporal, TemporalAdjusters, TemporalAmount, TemporalUnit, UnsupportedTemporalTypeException}
import java.time.{DayOfWeek, LocalDate, Period}

import scala.util.parsing.combinator.RegexParsers

/**
 * Shared "schedule" example (see examples/schedule.txt in the project root):
 * {{{
 *   2015-06-09 + 3M, 09.06.2015, (2015-01-15 : 1Q : 2015-12-31)
 * }}}
 * The parsers build a rule: a function of the context (base date and market) that is evaluated later.
 */
class ScheduleParsers extends RegexParsers {

  import ScheduleParsers._

  def number: Parser[Int] = """\d+""".r ^^ (_.toInt)

  def date: Parser[Context => LocalDate] = "T" ^^^ base | number ~ (
      ("-" ~> number) ~ ("-" ~> number) ^^ { case m ~ d => (y: Int) => LocalDate.of(y, m, d) }  // ISO
    | ("." ~> number) ~ ("." ~> number) ^^ { case m ~ y => (d: Int) => LocalDate.of(y, m, d) }  // German
    | ("/" ~> number) ~ ("/" ~> number) ^^ { case d ~ y => (m: Int) => LocalDate.of(y, m, d) }  // US
    ) ^^ { case first ~ toDate => val date = toDate(first); (_: Context) => date }

  def period: Parser[TemporalAmount] = number ~ "[DWMQYB]".r ^^ {
    case n ~ "D" => Period.ofDays(n)
    case n ~ "W" => Period.ofWeeks(n)
    case n ~ "M" => Period.ofMonths(n)
    case n ~ "Q" => Period.ofMonths(3 * n)
    case n ~ "Y" => Period.ofYears(n)
    case n ~ "B" => BusinessDays(n)
  }

  def pair: Parser[String] = "[A-Z]{6}".r

  def shift: Parser[(LocalDate, Market) => LocalDate] =
    ("+" ~> period ^^ (p => (d: LocalDate, m: Market) => plus(d, p, 1, m))
    | "-" ~> period ^^ (p => (d: LocalDate, m: Market) => plus(d, p, -1, m))
    | ">" ~ "IMM" ^^^ ((d: LocalDate, _: Market) => nextImm(d))
    | ">" ~ "SPOT" ~> opt(pair) ^^ (p => (d: LocalDate, m: Market) => m.spot(d, p)))

  /** shifts are applied from left to right */
  def expr: Parser[Context => LocalDate] = date ~ rep(shift) ^^ {
    case start ~ shifts => (c: Context) => shifts.foldLeft(start(c))((d, f) => f(d, c.market))
  }

  def range: Parser[Context => List[LocalDate]] = ("(" ~> expr) ~ (":" ~> period) ~ (":" ~> expr <~ ")") ^^ {
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
      (c: Context) => {
        val (start, end) = (first(c), last(c))
        Iterator.from(0).map(k => plus(start, times(k), 1, c.market)).takeWhile(!_.isAfter(end)).toList
      }
  }

  def schedule: Parser[Context => List[LocalDate]] =
    range | rep1sep(expr, ",") ^^ (exprs => (c: Context) => exprs.map(_(c)))
}

object ScheduleParsers {

  /** a market answers questions about days; the meaning of the rules doesn't depend on it */
  trait Market {
    def isBusinessDay(date: LocalDate): Boolean

    /** the FX spot date: T+1 for USD against CAD, TRY, PHP or RUB, otherwise T+2 (also without pair) */
    def spot(trade: LocalDate, pair: Option[String]): LocalDate = {
      val lag = if (pair.exists(_.matches("USD(CAD|TRY|PHP|RUB)|(CAD|TRY|PHP|RUB)USD"))) 1 else 2
      plusBusinessDays(trade, lag, this)
    }
  }

  object Market {
    /** Monday to Friday, no holidays */
    val Weekends: Market = _.getDayOfWeek.getValue < 6

    /** Monday to Friday except the holidays */
    def withHolidays(holidays: Set[LocalDate]): Market = date => Weekends.isBusinessDay(date) && !holidays(date)
  }

  /** what a rule is evaluated against: the base date (only needed for T) and the market */
  case class Context(base: Option[LocalDate] = None, market: Market = Market.Weekends)

  /** T: the base date of the evaluation */
  val base: Context => LocalDate = _.base.getOrElse(throw new IllegalStateException("no base date"))

  /** business days; which days these are depends on the market, so they are only applied by plus(…, market) */
  case class BusinessDays(count: Int) extends TemporalAmount {
    def get(unit: TemporalUnit): Long = throw new UnsupportedTemporalTypeException(s"business days have no unit: $unit")
    def getUnits: java.util.List[TemporalUnit] = java.util.List.of()
    def addTo(temporal: Temporal): Temporal = throw new UnsupportedTemporalTypeException("business days need a market")
    def subtractFrom(temporal: Temporal): Temporal = throw new UnsupportedTemporalTypeException("business days need a market")
  }

  /** the date plus amount (minus for sign -1): periods as in java.time, business days of the market */
  def plus(date: LocalDate, amount: TemporalAmount, sign: Int, market: Market): LocalDate = amount match {
    case BusinessDays(n) => plusBusinessDays(date, sign * n, market)
    case p => if (sign > 0) date.plus(p) else date.minus(p)
  }

  /** the date moved by count business days of the market, backwards if count is negative */
  def plusBusinessDays(date: LocalDate, count: Int, market: Market): LocalDate =
    if (count == 0) date
    else Iterator.iterate(date)(_.plusDays(count.sign)).drop(1)
      .filter(market.isBusinessDay).drop(count.abs - 1).next()

  /** the third Wednesday of March, June, September or December strictly after the date */
  def nextImm(date: LocalDate): LocalDate =
    Iterator.iterate(date.withDayOfMonth(1))(_.plusMonths(1))
      .filter(_.getMonthValue % 3 == 0)
      .map(_.`with`(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.WEDNESDAY)))
      .find(_.isAfter(date)).get

  /**
   * The rule of a schedule; evaluate it with a [[Context]].
   *
   * @throws IllegalArgumentException on a syntax error or a range without positive step
   * @throws java.time.DateTimeException on an invalid date
   */
  def parse(text: String): Context => List[LocalDate] = {
    val parsers = new ScheduleParsers
    parsers.parseAll(parsers.schedule, text) match {
      case parsers.Success(rule, _) => rule
      case failure: parsers.NoSuccess => throw new IllegalArgumentException(failure.toString)
    }
  }
}
