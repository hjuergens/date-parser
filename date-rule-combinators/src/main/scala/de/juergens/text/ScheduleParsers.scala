package de.juergens.text

import java.time.chrono.HijrahDate
import java.time.temporal.{ChronoField, JulianFields, Temporal, TemporalAdjusters, TemporalAmount, TemporalUnit, UnsupportedTemporalTypeException}
import java.time.{DateTimeException, DayOfWeek, LocalDate, Period}

import com.ibm.icu.util.{Calendar, HebrewCalendar}
import com.nlf.calendar.Solar

import scala.util.parsing.combinator.RegexParsers

/**
 * Shared "schedule" example (see examples/schedule.txt in the project root):
 * {{{
 *   2015-06-09 + 3M, 09.06.2015, (2015-01-15 : 1Q : 2015-12-31)
 * }}}
 * The parsers build a rule: a function of the context (base date, market and events) that is evaluated later.
 */
class ScheduleParsers extends RegexParsers {

  import ScheduleParsers._

  def number: Parser[Int] = """\d+""".r ^^ (_.toInt)

  // Gregorian, then Hijri (Umm al-Qura), Hebrew and Chinese months; the regex takes the first alternative, so ADARII before ADAR
  def month: Parser[String] = ("JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC"
    + "|MUHARRAM|SAFAR|RABIALAWWAL|RABIALTHANI|JUMADAALULA|JUMADAALAKHIRA|RAJAB|SHABAN|RAMADAN|SHAWWAL|DHULQADAH|DHULHIJJAH"
    + "|NISAN|IYAR|SIVAN|TAMMUZ|AV|ELUL|TISHRI|HESHVAN|KISLEV|TEVET|SHEVAT|ADARII|ADAR"
    + "|ZHENGYUE|ERYUE|SANYUE|SIYUE|WUYUE|LIUYUE|QIYUE|BAYUE|JIUYUE|SHIYUE|DONGYUE|LAYUE").r

  def weekday: Parser[String] = "MON|TUE|WED|THU|FRI|SAT|SUN".r

  /** dates that recur every year, such as 3 MON JAN */
  def annual: Parser[Annual] = (number ~ month ^^ { case d ~ m => (y: Int) =>
        if (months.contains(m)) LocalDate.of(y, months(m), d) else lunar(d, m, y) }
    | number ~ weekday ~ month ^^ { case n ~ w ~ m => nthWeekday(n, w, m) }
    | "LAST" ~> weekday ~ month ^^ { case w ~ m => (y: Int) =>
        LocalDate.of(y, months(m), 1).`with`(TemporalAdjusters.lastInMonth(weekdays(w))) }
    | "EASTER" ^^^ (easter _))

  // "TOM" before "T": without a lexer, "T" would match the start of "TOM"
  def date: Parser[Context => LocalDate] = eventName ^^ event | "TOM" ^^^ ((c: Context) => plusBusinessDays(base(c), 1, c.market)) |
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

  // without a lexer nothing reserves the six-letter keywords, so the pattern excludes them
  def pair: Parser[String] = "(?!EASTER|SHABAN|TAMMUZ|TISHRI|KISLEV|SHEVAT|ADARII|SANYUE|LIUYUE|JIUYUE|SHIYUE)[A-Z]{6}".r

  /** a named event such as @FOMC, without the sigil; the sigil keeps names apart from keywords and pairs */
  def eventName: Parser[String] = "@[A-Z][A-Z0-9_]*".r ^^ (_.tail)

  def anchor: Parser[Anchor] = (weekday ^^ (w => (d: LocalDate, dir: Int, _: Context) =>
        d.`with`(if (dir > 0) TemporalAdjusters.next(weekdays(w)) else TemporalAdjusters.previous(weekdays(w))))
    | "BD" ^^^ ((d: LocalDate, dir: Int, c: Context) => plusBusinessDays(d, dir, c.market))
    | "IMM" ^^^ ((d: LocalDate, dir: Int, _: Context) => if (dir > 0) nextImm(d) else previousImm(d))
    | annual ^^ (a => (d: LocalDate, dir: Int, _: Context) => {
        val matched = a(d.getYear)
        if (matched.compareTo(d).sign == dir) matched else a(d.getYear + dir)
      })
    | eventName ^^ eventAnchor)

  def observe: Parser[(DayOfWeek, TemporalAmount, Int)] = weekday ~ ("+" ^^^ 1 | "-" ^^^ -1) ~ period ^^ {
    case w ~ sign ~ p => (weekdays(w), p, sign)
  }

  def shift: Parser[(LocalDate, Context) => LocalDate] =
    ("+" ~> period ^^ (p => (d: LocalDate, c: Context) => plus(d, p, 1, c.market))
    | "-" ~> period ^^ (p => (d: LocalDate, c: Context) => plus(d, p, -1, c.market))
    | ">" ~ "IMM" ^^^ ((d: LocalDate, _: Context) => nextImm(d))
    | ">" ~ "SPOT" ~> opt(pair) ^^ (p => (d: LocalDate, c: Context) => c.market.spot(d, p))
    | ">" ~> "MODFOLLOWING|FOLLOWING|PRECEDING|BOM|EOM|BOY|EOY".r ^^ (r => (d: LocalDate, c: Context) => roll(d, r, c.market))
    | ">" ~ "NEXT" ~> anchor ^^ (a => (d: LocalDate, c: Context) => a(d, 1, c))
    | ">" ~ "PREV" ~> anchor ^^ (a => (d: LocalDate, c: Context) => a(d, -1, c))
    | "{" ~> rep1sep(observe, ",") <~ "}" ^^ (shifts => (d: LocalDate, c: Context) =>
        shifts.find(_._1 == d.getDayOfWeek).fold(d) { case (_, p, sign) => plus(d, p, sign, c.market) }))

  /** shifts are applied from left to right */
  def expr: Parser[Context => LocalDate] = date ~ rep(shift) ^^ {
    case start ~ shifts => (c: Context) => shifts.foldLeft(start(c))((d, f) => f(d, c))
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
        val (start, end) = (single(first, c), single(last, c))
        Iterator.from(0).map(k => plus(start, times(k), 1, c.market)).takeWhile(!_.isAfter(end)).toList
      }
  }

  /** 24 DEC IF MON TUE WED THU: the date only on these weekdays */
  def item: Parser[Context => Option[LocalDate]] = expr ~ opt("IF" ~> rep1(weekday)) ^^ {
    case e ~ None => (c: Context) => Some(e(c))
    case e ~ Some(days) => (c: Context) => Some(e(c)).filter(d => days.map(weekdays).contains(d.getDayOfWeek))
  }

  def schedule: Parser[Context => List[LocalDate]] =
    range | rep1sep(item, ",") ^^ (items => (c: Context) => items.flatMap(values(_, c).flatten))
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

  /** what a rule is evaluated against: the base date (only needed for T), the market and the named events;
   *  occurrence is the date of the event an expression is being evaluated for */
  case class Context(base: Option[LocalDate] = None, market: Market = Market.Weekends,
                     events: Map[String, Context => List[LocalDate]] = Map.empty, occurrence: Option[LocalDate] = None)

  /** T: the base date of the evaluation */
  val base: Context => LocalDate = _.base.getOrElse(throw new IllegalStateException("no base date"))

  /** thrown by an event date that is not bound to one occurrence yet; values() catches it */
  private class Unbound(val name: String) extends RuntimeException(s"unbound event $name", null, false, false)

  /** @NAME: one occurrence of the event, bound by values() */
  def event(name: String): Context => LocalDate = _.occurrence.getOrElse(throw new Unbound(name))

  /** the dates of the event in the year: its rule evaluated with the first of January as base date */
  def occurrences(name: String, year: Int, c: Context): List[LocalDate] = {
    val rule = c.events.getOrElse(name, throw new IllegalArgumentException(s"no event $name"))
    rule(Context(Some(LocalDate.of(year, 1, 1)), c.market, c.events)).filter(_.getYear == year).sortWith(_.isBefore(_))
  }

  /** the values of an expression: one, or one per occurrence in the base year if it starts with an event */
  def values[A](expr: Context => A, c: Context): List[A] =
    try List(expr(c))
    catch {
      case unbound: Unbound =>
        occurrences(unbound.name, base(c).getYear, c).map(d => expr(c.copy(occurrence = Some(d))))
    }

  /** the date of an expression that must have exactly one, such as the end of a range */
  def single(expr: Context => LocalDate, c: Context): LocalDate = values(expr, c) match {
    case List(d) => d
    case dates => throw new IllegalArgumentException(s"${dates.size} dates where one is needed")
  }

  /** the occurrence of the event strictly after (before) the date, in its year or the next (previous) */
  def eventAnchor(name: String): Anchor = (d: LocalDate, dir: Int, c: Context) =>
    List(d.getYear, d.getYear + dir).iterator
      .map(year => occurrences(name, year, c).filter(_.compareTo(d).sign == dir))
      .collectFirst { case found if found.nonEmpty => if (dir > 0) found.head else found.last }
      .getOrElse(throw new IllegalArgumentException(s"no $name ${if (dir > 0) "after" else "before"} $d"))

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
  type Anchor = (LocalDate, Int, Context) => LocalDate

  val months: Map[String, Int] =
    "JAN FEB MAR APR MAY JUN JUL AUG SEP OCT NOV DEC".split(" ").zipWithIndex.map { case (m, i) => m -> (i + 1) }.toMap

  val weekdays: Map[String, DayOfWeek] =
    "MON TUE WED THU FRI SAT SUN".split(" ").zipWithIndex.map { case (w, i) => w -> DayOfWeek.of(i + 1) }.toMap

  val hijri: Seq[String] = ("MUHARRAM SAFAR RABIALAWWAL RABIALTHANI JUMADAALULA JUMADAALAKHIRA RAJAB SHABAN RAMADAN SHAWWAL"
    + " DHULQADAH DHULHIJJAH").split(" ").toSeq
  val hebrew: Seq[String] = "NISAN IYAR SIVAN TAMMUZ AV ELUL TISHRI HESHVAN KISLEV TEVET SHEVAT ADAR ADARII".split(" ").toSeq
  val chinese: Seq[String] = "ZHENGYUE ERYUE SANYUE SIYUE WUYUE LIUYUE QIYUE BAYUE JIUYUE SHIYUE DONGYUE LAYUE".split(" ").toSeq

  // ICU's Hebrew months start with Tishri; ADAR_1 (5) only exists in a leap year, where ADAR (6) is Adar II
  private val icuHebrew = "TISHRI HESHVAN KISLEV TEVET SHEVAT ADAR ADAR NISAN IYAR SIVAN TAMMUZ AV ELUL".split(" ")

  /** the day of a Hijri (Umm al-Qura), Hebrew or Chinese month in the Gregorian year; an error if it occurs twice or not at all */
  def lunar(day: Int, month: String, year: Int): LocalDate = {
    val calendar: Calendar = new HebrewCalendar()
    def monthDay(date: LocalDate): (String, Int) =
      if (hijri.contains(month)) {
        val h = HijrahDate.from(date)
        (hijri(h.get(ChronoField.MONTH_OF_YEAR) - 1), h.get(ChronoField.DAY_OF_MONTH))
      } else if (chinese.contains(month)) {
        // 6tail's lunar: ICU's astronomical Chinese calendar misses some new moons close to midnight
        val lunar = Solar.fromYmd(date.getYear, date.getMonthValue, date.getDayOfMonth).getLunar
        (if (lunar.getMonth > 0) chinese(lunar.getMonth - 1) else "", lunar.getDay)   // a leap month is negative
      } else {
        calendar.clear()
        calendar.set(Calendar.JULIAN_DAY, date.getLong(JulianFields.JULIAN_DAY).toInt)
        val m = calendar.get(Calendar.MONTH)
        (if (m == 6 && (7 * calendar.get(Calendar.EXTENDED_YEAR) + 1) % 19 < 7) "ADARII" else icuHebrew(m), calendar.get(Calendar.DAY_OF_MONTH))
      }
    val hits = Iterator.iterate(LocalDate.of(year, 1, 1))(_.plusDays(1)).takeWhile(_.getYear == year)
      .filter(monthDay(_) == (month, day)).toList
    if (hits.size != 1) throw new DateTimeException(s"$day $month occurs ${hits.size} times in $year")
    hits.head
  }

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
