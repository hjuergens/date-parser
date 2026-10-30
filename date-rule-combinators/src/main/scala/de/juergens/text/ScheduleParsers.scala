package de.juergens.text

import java.time.temporal.{Temporal, TemporalAdjusters, TemporalAmount, TemporalUnit, UnsupportedTemporalTypeException}
import java.time.{DateTimeException, DayOfWeek, LocalDate, Period}

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

  def month: Parser[String] = "JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC".r

  def weekday: Parser[String] = "MON|TUE|WED|THU|FRI|SAT|SUN".r

  /** dates that recur every year, such as 3 MON JAN */
  def annual: Parser[Annual] = (number ~ month ^^ { case d ~ m => (y: Int) => LocalDate.of(y, months(m), d) }
    | number ~ weekday ~ month ^^ { case n ~ w ~ m => nthWeekday(n, w, m) }
    | "LAST" ~> weekday ~ month ^^ { case w ~ m => (y: Int) =>
        LocalDate.of(y, months(m), 1).`with`(TemporalAdjusters.lastInMonth(weekdays(w))) }
    | "EASTER" ^^^ (easter _))

  // "TOM" before "T": without a lexer, "T" would match the start of "TOM"
  def date: Parser[Context => LocalDate] = "TOM" ^^^ ((c: Context) => plusBusinessDays(base(c), 1, c.market)) |
    "T" ^^^ base |
    "SPOT" ~> opt(pair) ^^ (p => (c: Context) => c.market.spot(base(c), p)) |
    annual ^^ (a => (c: Context) => a(base(c).getYear)) | number ~ (
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

  // without a lexer nothing reserves the keyword EASTER, so the pattern excludes it
  def pair: Parser[String] = "(?!EASTER)[A-Z]{6}".r

  def anchor: Parser[Anchor] = (weekday ^^ (w => (d: LocalDate, dir: Int, _: Market) =>
        d.`with`(if (dir > 0) TemporalAdjusters.next(weekdays(w)) else TemporalAdjusters.previous(weekdays(w))))
    | "BD" ^^^ ((d: LocalDate, dir: Int, m: Market) => plusBusinessDays(d, dir, m))
    | "IMM" ^^^ ((d: LocalDate, dir: Int, _: Market) => if (dir > 0) nextImm(d) else previousImm(d))
    | annual ^^ (a => (d: LocalDate, dir: Int, _: Market) => {
        val matched = a(d.getYear)
        if (matched.compareTo(d).sign == dir) matched else a(d.getYear + dir)
      }))

  def observe: Parser[(DayOfWeek, TemporalAmount, Int)] = weekday ~ ("+" ^^^ 1 | "-" ^^^ -1) ~ period ^^ {
    case w ~ sign ~ p => (weekdays(w), p, sign)
  }

  def shift: Parser[(LocalDate, Market) => LocalDate] =
    ("+" ~> period ^^ (p => (d: LocalDate, m: Market) => plus(d, p, 1, m))
    | "-" ~> period ^^ (p => (d: LocalDate, m: Market) => plus(d, p, -1, m))
    | ">" ~ "IMM" ^^^ ((d: LocalDate, _: Market) => nextImm(d))
    | ">" ~ "SPOT" ~> opt(pair) ^^ (p => (d: LocalDate, m: Market) => m.spot(d, p))
    | ">" ~> "MODFOLLOWING|FOLLOWING|PRECEDING|BOM|EOM|BOY|EOY".r ^^ (r => (d: LocalDate, m: Market) => roll(d, r, m))
    | ">" ~ "NEXT" ~> anchor ^^ (a => (d: LocalDate, m: Market) => a(d, 1, m))
    | ">" ~ "PREV" ~> anchor ^^ (a => (d: LocalDate, m: Market) => a(d, -1, m))
    | "{" ~> rep1sep(observe, ",") <~ "}" ^^ (shifts => (d: LocalDate, m: Market) =>
        shifts.find(_._1 == d.getDayOfWeek).fold(d) { case (_, p, sign) => plus(d, p, sign, m) }))

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

    /** Monday to Friday except the dates the rule yields with the first of January of the year before, the year
     *  and the year after as base date (an observed holiday can fall into the neighbouring year) */
    def withHolidayRules(rule: Context => List[LocalDate]): Market = {
      val years = scala.collection.mutable.Map.empty[Int, List[LocalDate]]
      date => Weekends.isBusinessDay(date) && !(date.getYear - 1 to date.getYear + 1).exists(year =>
        years.getOrElseUpdate(year, rule(Context(Some(LocalDate.of(year, 1, 1))))).contains(date))
    }
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

  /** the third Wednesday of March, June, September or December strictly before the date */
  def previousImm(date: LocalDate): LocalDate =
    Iterator.iterate(date.withDayOfMonth(1))(_.minusMonths(1))
      .filter(_.getMonthValue % 3 == 0)
      .map(_.`with`(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.WEDNESDAY)))
      .find(_.isBefore(date)).get

  /** a date that recurs every year */
  type Annual = Int => LocalDate

  /** what > NEXT and > PREV look for: the first match strictly after (direction 1) or before (-1) the date */
  type Anchor = (LocalDate, Int, Market) => LocalDate

  val months: Map[String, Int] =
    "JAN FEB MAR APR MAY JUN JUL AUG SEP OCT NOV DEC".split(" ").zipWithIndex.map { case (m, i) => m -> (i + 1) }.toMap

  val weekdays: Map[String, DayOfWeek] =
    "MON TUE WED THU FRI SAT SUN".split(" ").zipWithIndex.map { case (w, i) => w -> DayOfWeek.of(i + 1) }.toMap

  /** the n-th weekday of the month; there is no fifth Monday in every month */
  def nthWeekday(n: Int, weekday: String, month: String): Annual = year => {
    val date = LocalDate.of(year, months(month), 1).`with`(TemporalAdjusters.nextOrSame(weekdays(weekday))).plusWeeks(n - 1)
    if (n < 1 || date.getMonthValue != months(month)) throw new DateTimeException(s"no $n $weekday $month in $year")
    date
  }

  /** Western Easter Sunday (anonymous Gregorian algorithm) */
  def easter(year: Int): LocalDate = {
    val (a, b, c) = (year % 19, year / 100, year % 100)
    val (d, e, g) = (b / 4, b % 4, (8 * b + 13) / 25)
    val h = (19 * a + b - d - g + 15) % 30
    val l = (32 + 2 * e + 2 * (c / 4) - h - c % 4) % 7
    val m = (a + 11 * h + 22 * l) / 451
    LocalDate.of(year, (h + l - 7 * m + 114) / 31, (h + l - 7 * m + 114) % 31 + 1)
  }

  /** FOLLOWING, PRECEDING, MODFOLLOWING (following unless that leaves the month); BOM, EOM, BOY, EOY */
  def roll(date: LocalDate, convention: String, market: Market): LocalDate = convention match {
    case "FOLLOWING" => if (market.isBusinessDay(date)) date else plusBusinessDays(date, 1, market)
    case "PRECEDING" => if (market.isBusinessDay(date)) date else plusBusinessDays(date, -1, market)
    case "MODFOLLOWING" =>
      val following = roll(date, "FOLLOWING", market)
      if (following.getMonth == date.getMonth) following else roll(date, "PRECEDING", market)
    case "BOM" => date.withDayOfMonth(1)
    case "EOM" => date.`with`(TemporalAdjusters.lastDayOfMonth())
    case "BOY" => date.withDayOfYear(1)
    case "EOY" => date.`with`(TemporalAdjusters.lastDayOfYear())
  }

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
