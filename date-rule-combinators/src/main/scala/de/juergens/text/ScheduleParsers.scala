package de.juergens.text

import java.time.chrono.HijrahDate
import java.time.temporal.{ChronoField, JulianFields, Temporal, TemporalAdjusters, TemporalAmount, TemporalUnit, UnsupportedTemporalTypeException}
import java.time.{DateTimeException, DayOfWeek, LocalDate, Period}

import com.ibm.icu.util.{Calendar, HebrewCalendar}
import com.nlf.calendar.Solar
import de.juergens.time.Season

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
  def annual: Parser[Annual] = fixedAnnual | lastWeekday

  def lastWeekday: Parser[Annual] = "LAST" ~> weekday ~ month ^^ { case w ~ m => (y: Int) =>
        LocalDate.of(y, months(m), 1).`with`(TemporalAdjusters.lastInMonth(weekdays(w))) }

  /** the annual dates that don't start with LAST, which also starts the count of a selection */
  def fixedAnnual: Parser[Annual] = (number ~ month ^^ { case d ~ m => (y: Int) =>
        if (months.contains(m)) LocalDate.of(y, months(m), d) else lunar(d, m, y) }
    | number ~ weekday ~ month ^^ { case n ~ w ~ m => nthWeekday(n, w, m) }
    | "EASTER" ^^^ (easter _)
    | ("EQUINOX" | "SOLSTICE") ~ month ~ zone ^^ { case k ~ m ~ z => season(k, m, z) }
    | "SOLARTERM" ~> number ~ zone ^^ { case n ~ z => solarTerm(n, z) })

  /** the time zone of an equinox, solstice or solar term in minutes: UTC unless an offset follows */
  def zone: Parser[Int] = opt("UTC[+-][0-9]+".r) ^^ (_.fold(0)(_.substring(3).toInt * 60))

  // "TOM" before "T": without a lexer, "T" would match the start of "TOM"
  def date: Parser[Context => LocalDate] = series | singleDate

  /** a date that is not a series */
  def singleDate: Parser[Context => LocalDate] = eventName ^^ event | "TOM" ^^^ ((c: Context) => plusBusinessDays(base(c), 1, c.market)) |
    "T" ^^^ base |
    "SPOT" ~> opt(pair) ^^ (p => (c: Context) => c.market.spot(base(c), p)) |
    annual ^^ (a => (c: Context) => a(base(c).getYear)) | number ~ (
      ("-" ~> number) ~ ("-" ~> number) ^^ { case m ~ d => (y: Int) => LocalDate.of(y, m, d) }  // ISO
    | ("." ~> number) ~ ("." ~> number) ^^ { case m ~ y => (d: Int) => LocalDate.of(y, m, d) }  // German
    | ("/" ~> number) ~ ("/" ~> number) ^^ { case d ~ y => (m: Int) => LocalDate.of(y, m, d) }  // US
    ) ^^ { case first ~ toDate => val date = toDate(first); (_: Context) => date }

  def period: Parser[TemporalAmount] = number ~ "[DWMQHYBF]".r ^^ {
    case n ~ "D" => Period.ofDays(n)
    case n ~ "W" => Period.ofWeeks(n)
    case n ~ "M" => Period.ofMonths(n)
    case n ~ "Q" => Period.ofMonths(3 * n)
    case n ~ "H" => Period.ofMonths(6 * n)
    case n ~ "Y" => Period.ofYears(n)
    case n ~ "B" => BusinessDays(n)
    case n ~ "F" => FixingDays(n)
  }

  // without a lexer nothing reserves the six-letter keywords, so the pattern excludes them
  def pair: Parser[String] = "(?!EASTER|SHABAN|TAMMUZ|TISHRI|KISLEV|SHEVAT|ADARII|SANYUE|LIUYUE|JIUYUE|SHIYUE)[A-Z]{6}".r

  /** a named event such as @FOMC, without the sigil; the sigil keeps names apart from keywords and pairs */
  def eventName: Parser[String] = "@[A-Z][A-Z0-9_]*".r ^^ (_.tail)

  /** NEXT 3RD FRI: the third Friday; the ordinal is tried before the number of 3 FRI JAN could take the digits */
  def counted: Parser[Anchor] = opt(ordinal) ~ anchor ^^ { case n ~ a => n.fold(a)(times(_, a)) }

  /** 1ST, 2ND, 3RD, 4TH, 11TH, 21ST; the suffix must fit the number */
  def ordinal: Parser[Int] = "[0-9]+(ST|ND|RD|TH)".r >> { text =>
    val n = text.dropRight(2).toInt
    val suffix = if (n % 100 >= 11 && n % 100 <= 13) "TH"
      else n % 10 match { case 1 => "ST" case 2 => "ND" case 3 => "RD" case _ => "TH" }
    if (text.endsWith(suffix)) success(n) else failure(s"not an ordinal: $text")
  }

  /** NEXT {MON, FRI}, NEXT {1 FEB, 1 MAY}: the nearest match of any of the anchors */
  def anchor: Parser[Anchor] = "{" ~> rep1sep(target, ",") <~ "}" ^^ anyOf | target

  def anyOf(anchors: List[Anchor]): Anchor = (d: LocalDate, dir: Int, c: Context) => {
    val found = anchors.map(_(d, dir, c))
    if (dir > 0) found.minBy(_.toEpochDay) else found.maxBy(_.toEpochDay)
  }

  def target: Parser[Anchor] = chosen | lastWeekday ^^ annualAnchor

  def chosen: Parser[Anchor] = (weekday ^^ (w => (d: LocalDate, dir: Int, _: Context) =>
        d.`with`(if (dir > 0) TemporalAdjusters.next(weekdays(w)) else TemporalAdjusters.previous(weekdays(w))))
    | "BD" ^^^ ((d: LocalDate, dir: Int, c: Context) => plusBusinessDays(d, dir, c.market))
    | "FD" ^^^ ((d: LocalDate, dir: Int, c: Context) => plusBusinessDays(d, dir, c.fixing))
    | "IMM" ^^^ ((d: LocalDate, dir: Int, _: Context) => if (dir > 0) nextImm(d) else previousImm(d))
    | fixedAnnual ^^ annualAnchor
    | eventName ^^ eventAnchor)

  def annualAnchor(a: Annual): Anchor = (d: LocalDate, dir: Int, _: Context) => {
    val matched = a(d.getYear)
    if (matched.compareTo(d).sign == dir) matched else a(d.getYear + dir)
  }

  /** > 3RD WED IN MONTH, > 2ND LAST BD IN MONTH: the n-th match in the period of the date, from its start or its end;
   *  the targets of NEXT, an annual date starting with LAST only in braces */
  def selection: Parser[(LocalDate, Context) => LocalDate] =
    (ordinal ~ opt("LAST") ^^ { case n ~ last => (n, last.isDefined) } | "LAST" ^^^ ((1, true))) ~
      ("{" ~> rep1sep(target, ",") <~ "}" ^^ anyOf | chosen) ~ ("IN" ~> "WEEK|MONTH|QUARTER|HALFYEAR|YEAR".r) ^^ {
        case (count, fromEnd) ~ anchor ~ period => (d: LocalDate, c: Context) => select(d, count, fromEnd, anchor, period, c)
      }

  def observe: Parser[(DayOfWeek, TemporalAmount, Int)] = weekday ~ ("+" ^^^ 1 | "-" ^^^ -1) ~ period ^^ {
    case w ~ sign ~ p => (weekdays(w), p, sign)
  }

  def shift: Parser[(LocalDate, Context) => LocalDate] =
    ("+" ~> period ^^ (p => (d: LocalDate, c: Context) => plus(d, p, 1, c.market, c.fixing))
    | "-" ~> period ^^ (p => (d: LocalDate, c: Context) => plus(d, p, -1, c.market, c.fixing))
    | ">" ~ "IMM" ^^^ ((d: LocalDate, _: Context) => nextImm(d))
    | ">" ~ "SPOT" ~> opt(pair) ^^ (p => (d: LocalDate, c: Context) => c.market.spot(d, p))
    | ">" ~ "FIX" ~> opt(pair) ^^ (p => (d: LocalDate, c: Context) => plusBusinessDays(d, -Market.spotLag(p), c.fixing))
    | ">" ~> "MODFOLLOWING|FOLLOWING|PRECEDING|BOW|EOW|BOM|EOM|BOQ|EOQ|BOH|EOH|BOY|EOY".r ^^ (r => (d: LocalDate, c: Context) => roll(d, r, c.market))
    | ">" ~> selection
    | ">" ~ "{" ~> rep1sep(annual, ",") <~ "}" ^^ (annuals => (d: LocalDate, c: Context) =>
        // > {1 FEB, 1 MAY}: the dates of the set in the year of the date
        take(c, _ => annuals.map(_(d.getYear)).distinct.sortWith(_.isBefore(_))))
    | ">" ~ "NEXT" ~> counted ^^ (a => (d: LocalDate, c: Context) => a(d, 1, c))
    | ">" ~ "PREV" ~> counted ^^ (a => (d: LocalDate, c: Context) => a(d, -1, c))
    | "{" ~> rep1sep(observe, ",") <~ "}" ^^ (shifts => (d: LocalDate, c: Context) =>
        shifts.find(_._1 == d.getDayOfWeek).fold(d) { case (_, p, sign) => plus(d, p, sign, c.market, c.fixing) }))

  /** shifts are applied from left to right */
  def expr: Parser[Context => LocalDate] = date ~ rep(shift) ^^ {
    case start ~ shifts => (c: Context) => shifts.foldLeft(start(c))((d, f) => f(d, c))
  }

  /** [a : step : b], every day without a step; "]" at the start or "[" at the end leaves that endpoint out. A series
   *  is a date with several values, like an event: the shifts after it apply to each element */
  def series: Parser[Context => LocalDate] =
    ("[" ^^^ false | "]" ^^^ true) ~ endpoint ~
      (":" ~> ((period <~ ":") ~ opt(endpoint) ^^ { case p ~ e => (Option(p), e) } | endpoint ^^ (e => (None, Some(e))))) ~
      ("]" ^^^ false | "[" ^^^ true) ^^ {
      case startOut ~ first ~ ((step, last)) ~ endOut =>
        require(last.isDefined || !endOut, "an open series ends with ]")
        val every = step.getOrElse(Period.ofDays(1))
        val positive = every match {
          case p: Period => !p.isZero && !p.isNegative
          case BusinessDays(n) => n > 0
          case FixingDays(n) => n > 0
        }
        require(positive, s"step must be positive: $every")
        def times(k: Int): TemporalAmount = every match {
          case p: Period => p.multipliedBy(k)
          case BusinessDays(n) => BusinessDays(k * n)
          case FixingDays(n) => FixingDays(k * n)
        }
        (c: Context) => take(c, c => {
          val start = single(first, c)
          val elements = Iterator.from(0).map(k => plus(start, times(k), 1, c.market, c.fixing))
          last match {
            case Some(lastExpr) =>
              val end = single(lastExpr, c)
              elements.takeWhile(!_.isAfter(end))
                .filterNot(d => startOut && !d.isAfter(start) || endOut && !d.isBefore(end)).toList
            case None =>   // an open series [first : step : ], only under a pick, which sets how many elements it yields
              val limit = c.limit.getOrElse(throw new IllegalArgumentException("an open series needs a pick in front of the item"))
              elements.take(limit).filterNot(d => startOut && !d.isAfter(start)).toList
          }
        })
    }

  /** an endpoint is one date, so it can't be a series: after the last ":" a "]" closes an open series */
  def endpoint: Parser[Context => LocalDate] = singleDate ~ rep(shift) ^^ {
    case start ~ shifts => (c: Context) => shifts.foldLeft(start(c))((d, f) => f(d, c))
  }

  /** A & B includes the dates of B, A \\ B excludes them; the shifts after it apply to the result */
  def combine: Parser[(Context => LocalDate) => (Context => LocalDate)] =
    ("&" ^^^ true | "\\" ^^^ false) ~ operand ~ rep(shift) ^^ { case include ~ o ~ shifts => (left: Context => LocalDate) =>
      val node = combined(left, include, o)
      (c: Context) => shifts.foldLeft(node(c))((d, f) => f(d, c))
    }

  /** a date, a series or a set of dates */
  def operand: Parser[List[Context => LocalDate]] = date ^^ (List(_)) | "{" ~> rep1sep(expr, ",") <~ "}"

  /** 24 DEC IF MON TUE WED THU, IF {MON, TUE}: the date only on these weekdays */
  def item: Parser[Context => Option[LocalDate]] = pickedItem | plainItem

  /** 5TH [SPOT : 1Q : ] > EOQ > 3RD WED IN MONTH: the 5th date after T of the item, 2ND LAST from the last one; the item
   *  starts with a series or an event, where several dates come from */
  def pickedItem: Parser[Context => Option[LocalDate]] =
    ordinal ~ opt("LAST") ~ (series | eventName ^^ event) ~ rep(shift) ~ rest ^^ {
      case n ~ last ~ start ~ shifts ~ finish =>
        val item = finish((c: Context) => shifts.foldLeft(start(c))((d, f) => f(d, c)))
        (c: Context) => Some(pick(n, last.isDefined, item, c))
    }

  def plainItem: Parser[Context => Option[LocalDate]] = expr ~ rest ^^ { case first ~ finish => finish(first) }

  /** the combines and the IF of an item */
  def rest: Parser[(Context => LocalDate) => (Context => Option[LocalDate])] =
    rep(combine) ~ opt("IF" ~> (rep1(weekday) | "{" ~> rep1sep(weekday, ",") <~ "}")) ^^ {
    case combines ~ days => (first: Context => LocalDate) =>
      val e = combines.foldLeft(first)((left, c) => c(left))
      days match {
        case None => (c: Context) => Some(e(c))
        case Some(days) => (c: Context) => Some(e(c)).filter(d => days.map(weekdays).contains(d.getDayOfWeek))
      }
  }

  def schedule: Parser[Context => List[LocalDate]] =
    rep1sep(item, ",") ^^ (items => (c: Context) => items.flatMap(values(_, c).flatten))
}

object ScheduleParsers {

  /** a market answers questions about days; the meaning of the rules doesn't depend on it */
  trait Market {
    def isBusinessDay(date: LocalDate): Boolean

    /** the FX spot date: T+1 for USD against CAD, TRY, PHP or RUB, otherwise T+2 (also without pair) */
    def spot(trade: LocalDate, pair: Option[String]): LocalDate = {
      plusBusinessDays(trade, Market.spotLag(pair), this)
    }
  }

  object Market {
    /** Monday to Friday, no holidays */
    val Weekends: Market = _.getDayOfWeek.getValue < 6

    /** the business days from trade to spot: 1 for USD against CAD, TRY, PHP or RUB, otherwise 2 (also without pair) */
    def spotLag(pair: Option[String]): Int = if (pair.exists(_.matches("USD(CAD|TRY|PHP|RUB)|(CAD|TRY|PHP|RUB)USD"))) 1 else 2

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
   *  bindings are the values values() chose for the nodes with several values (events, series, combinations, dates in
   *  year), in the order the evaluation reaches them, counted by cursor */
  case class Context(base: Option[LocalDate] = None, market: Market = Market.Weekends,
                     events: Map[String, Context => List[LocalDate]] = Map.empty, bindings: List[LocalDate] = Nil,
                     cursor: Array[Int] = null, limit: Option[Int] = None, fixing: Market = Market.Weekends)

  /** T: the base date of the evaluation */
  val base: Context => LocalDate = _.base.getOrElse(throw new IllegalStateException("no base date"))

  /** thrown by a node with several values that is not bound yet; values() catches it and evaluates the expression once
   *  per date of dates */
  private class Unbound(val dates: Context => List[LocalDate]) extends RuntimeException("unbound", null, false, false)

  /** the count-th date of the item strictly after T, in the order the item yields them, or from the last one; an open
   *  series grows until there are enough dates, up to 10000 elements */
  def pick(count: Int, fromEnd: Boolean, item: Context => Option[LocalDate], c: Context): LocalDate = {
    require(count >= 1, s"count must be positive: $count")
    val t = base(c)
    val limits: List[Option[Int]] = if (fromEnd) List(None) else List(16, 256, 4096, 10000).map(Some(_))
    limits.iterator.map(limit => values(item, c.copy(limit = limit)).flatten.filter(_.isAfter(t)))
      .find(_.size >= count).map(hits => if (fromEnd) hits(hits.size - count) else hits(count - 1))
      .getOrElse(throw new IllegalArgumentException(s"no $count. date after $t"))
  }

  /** the value of the next node with several values in this evaluation: bound by values(), or Unbound */
  def take(c: Context, dates: Context => List[LocalDate]): LocalDate =
    if (c.cursor != null && c.cursor(0) < c.bindings.size) {
      c.cursor(0) += 1
      c.bindings(c.cursor(0) - 1)
    } else throw new Unbound(dates)

  /** @NAME: one occurrence of the event, bound by values() */
  def event(name: String): Context => LocalDate = c => take(c, c => occurrences(name, base(c).getYear, c))

  /** A & B, A \\ B: the dates of A with or without those of the operand, sorted and without duplicates; one value at a
   *  time, so the shifts after it apply to each */
  def combined(left: Context => LocalDate, include: Boolean, operand: List[Context => LocalDate]): Context => LocalDate =
    c => take(c, c => {
      val (l, r) = (values(left, c).toSet, operand.flatMap(values(_, c)).toSet)
      (if (include) l ++ r else l -- r).toList.sortWith(_.isBefore(_))
    })

  /** the dates of the event in the year: its rule evaluated with the first of January as base date */
  def occurrences(name: String, year: Int, c: Context): List[LocalDate] = {
    val rule = c.events.getOrElse(name, throw new IllegalArgumentException(s"no event $name"))
    rule(Context(Some(LocalDate.of(year, 1, 1)), c.market, c.events, fixing = c.fixing)).filter(_.getYear == year).sortWith(_.isBefore(_))
  }

  /** the values of an expression: one, or one per combination of the values of its nodes with several values (an
   *  event's occurrences in the base year, a series' elements, ...), bound one after the other */
  def values[A](expr: Context => A, c: Context, bindings: List[LocalDate] = Nil): List[A] =
    try List(expr(c.copy(bindings = bindings, cursor = Array(0))))
    catch {
      case unbound: Unbound => unbound.dates(c).flatMap(d => values(expr, c, bindings :+ d))
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

  /** fixing days; which days these are depends on the fixing calendar, so they are only applied by plus(…, fixing) */
  case class FixingDays(count: Int) extends TemporalAmount {
    def get(unit: TemporalUnit): Long = throw new UnsupportedTemporalTypeException(s"fixing days have no unit: $unit")
    def getUnits: java.util.List[TemporalUnit] = java.util.List.of()
    def addTo(temporal: Temporal): Temporal = throw new UnsupportedTemporalTypeException("fixing days need a fixing calendar")
    def subtractFrom(temporal: Temporal): Temporal = throw new UnsupportedTemporalTypeException("fixing days need a fixing calendar")
  }

  /** the date plus amount (minus for sign -1): periods as in java.time, business days of the market, fixing days of the
   *  fixing calendar */
  def plus(date: LocalDate, amount: TemporalAmount, sign: Int, market: Market, fixing: Market = Market.Weekends): LocalDate = amount match {
    case FixingDays(n) => plusBusinessDays(date, sign * n, fixing)
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

  /** the anchor found n times, each time from the one before */
  def times(n: Int, anchor: Anchor): Anchor = (d: LocalDate, dir: Int, c: Context) => {
    if (n < 1) throw new IllegalArgumentException(s"count must be positive: $n")
    (1 to n).foldLeft(d)((x, _) => anchor(x, dir, c))
  }

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

  /** EQUINOX MAR, SOLSTICE JUN, EQUINOX SEP, SOLSTICE DEC: the sun at 0, 90, 180, 270 degrees, in UTC + offset minutes */
  def season(kind: String, month: String, offset: Int): Annual = year => {
    val index = Seq("MAR", "JUN", "SEP", "DEC").indexOf(month)
    if (index < 0 || (index % 2 == 0) != (kind == "EQUINOX")) throw new DateTimeException(s"no $kind in $month")
    Season.sunAt(90 * index, year, offset)
  }

  /** SOLARTERM 1 to 24, counted from Lichun (315 degrees); SOLARTERM 5 is Qingming (15 degrees) */
  def solarTerm(term: Int, offset: Int): Annual = year => {
    if (term < 1 || term > 24) throw new DateTimeException(s"no solar term $term")
    Season.sunAt((term + 20) % 24 * 15, year, offset)
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

  /** the anchor found count times from the day before the period of the date starts, or backwards from the day after it
   *  ends; an error if that leaves the period */
  def select(date: LocalDate, count: Int, fromEnd: Boolean, anchor: Anchor, period: String, c: Context): LocalDate = {
    if (count < 1) throw new IllegalArgumentException(s"count must be positive: $count")
    val unit = if (period == "HALFYEAR") "H" else period.take(1)
    val (first, last) = (roll(date, "BO" + unit, c.market), roll(date, "EO" + unit, c.market))
    val found = (1 to count).foldLeft(if (fromEnd) last.plusDays(1) else first.minusDays(1))((d, _) =>
      anchor(d, if (fromEnd) -1 else 1, c))
    if (found.isBefore(first) || found.isAfter(last))
      throw new IllegalArgumentException(s"no $count. match in the ${period.toLowerCase} of $date")
    found
  }

  /** FOLLOWING, PRECEDING, MODFOLLOWING (following unless that leaves the month); BOW, EOW (Monday to Sunday), BOM, EOM,
   *  BOQ, EOQ, BOH, EOH, BOY, EOY */
  def roll(date: LocalDate, convention: String, market: Market): LocalDate = convention match {
    case "FOLLOWING" => if (market.isBusinessDay(date)) date else plusBusinessDays(date, 1, market)
    case "PRECEDING" => if (market.isBusinessDay(date)) date else plusBusinessDays(date, -1, market)
    case "MODFOLLOWING" =>
      val following = roll(date, "FOLLOWING", market)
      if (following.getMonth == date.getMonth) following else roll(date, "PRECEDING", market)
    case "BOW" => date.`with`(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    case "EOW" => date.`with`(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    case "BOM" => date.withDayOfMonth(1)
    case "EOM" => date.`with`(TemporalAdjusters.lastDayOfMonth())
    case "BOQ" => date.withDayOfMonth(1).withMonth((date.getMonthValue - 1) / 3 * 3 + 1)
    case "EOQ" => roll(date, "BOQ", market).plusMonths(2).`with`(TemporalAdjusters.lastDayOfMonth())
    case "BOH" => date.withDayOfMonth(1).withMonth((date.getMonthValue - 1) / 6 * 6 + 1)
    case "EOH" => roll(date, "BOH", market).plusMonths(5).`with`(TemporalAdjusters.lastDayOfMonth())
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
