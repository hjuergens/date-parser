package de.juergens.text.rules

import java.time.LocalDate
import java.time.temporal.TemporalAmount
import java.util.{List => JList, Map => JMap, Set => JSet}

import de.juergens.text.RuleCatalog.{CalendarSpec, Citation, Condition, IfClause, Move, Named, Postpone, Scope}
import de.juergens.text.ScheduleSemantics.{Anchor, Annual, Expr, Rule}
import de.juergens.text.{RuleCatalog, ScheduleSemantics}

import scala.jdk.CollectionConverters._
import scala.util.parsing.combinator.RegexParsers

/**
 * Rule files: the schedule language inside declarations with scope, validity, kind and provenance, the same language
 * as date-rule-antlr-java's RuleFile.g4 and date-rule-lark's rules.lark (shared cases: examples/rules.txt). The parsers
 * build the declarations with RuleCatalog and ScheduleSemantics, the Java classes all JVM modules share (here in
 * src/main/java); dates are Expr, shifts RuleCatalog.Move.
 *
 * Parser combinators choose like a PEG: an alternative that succeeded is not tried again, so the order of the
 * alternatives matters, and every word is matched with kw, which needs a word boundary after it (T is not the start of
 * TOM, OR not the start of ORTHODOX). As a parser can backtrack after an @reference, the references of a rule are read
 * from the text of its declaration, without strings and comments.
 */
class RuleFiles private () extends RegexParsers {

  import RuleFiles._

  // whitespace and comments (# to the end of the line) between tokens
  override protected val whiteSpace = """(\s|#[^\n]*)+""".r

  /** a word: a keyword with a word boundary after it */
  def kw(word: String): Parser[String] = (java.util.regex.Pattern.quote(word) + "(?![A-Za-z0-9_])").r

  def number: Parser[Int] = """\d+""".r ^^ (_.toInt)

  /** 1ST, 2ND, 3RD, 4TH; ScheduleSemantics.ordinal checks the suffix */
  def ordinal: Parser[Int] = "[0-9]+(ST|ND|RD|TH)(?![A-Z])".r ^^ (t => ScheduleSemantics.ordinal(t))

  // Gregorian, then Hijri (Umm al-Qura), Hebrew and Chinese months; the regex takes the first alternative, so ADARII before ADAR
  def month: Parser[String] = ("(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC"
    + "|MUHARRAM|SAFAR|RABIALAWWAL|RABIALTHANI|JUMADAALULA|JUMADAALAKHIRA|RAJAB|SHABAN|RAMADAN|SHAWWAL|DHULQADAH|DHULHIJJAH"
    + "|NISAN|IYAR|SIVAN|TAMMUZ|AV|ELUL|TISHRI|HESHVAN|KISLEV|TEVET|SHEVAT|ADARII|ADAR"
    + "|ZHENGYUE|ERYUE|SANYUE|SIYUE|WUYUE|LIUYUE|QIYUE|BAYUE|JIUYUE|SHIYUE|DONGYUE|LAYUE)(?![A-Z])").r

  def weekday: Parser[String] = "(MON|TUE|WED|THU|FRI|SAT|SUN)(?![A-Z])".r

  def roll: Parser[String] = "(MODFOLLOWING|MODPRECEDING|FOLLOWING|PRECEDING|BOW|EOW|BOM|EOM|BOQ|EOQ|BOH|EOH|BOY|EOY)(?![A-Z])".r

  def periodWord: Parser[String] = "(WEEK|MONTH|QUARTER|HALFYEAR|YEAR)(?![A-Z])".r

  // without a lexer nothing reserves the six-letter keywords, so the pattern excludes them
  def pair: Parser[String] =
    "(?!EASTER|ENDEND|FOLLOW|SHABAN|TAMMUZ|TISHRI|KISLEV|SHEVAT|ADARII|SANYUE|LIUYUE|JIUYUE|SHIYUE)[A-Z]{6}(?![A-Z])".r

  /** a currency, only inside "unless {...}" */
  def ccy: Parser[String] = "[A-Z]{3}(?![A-Z])".r

  /** a reference to a rule, an event class or an event, without the sigil */
  def eventName: Parser[String] = Reference ^^ (_.tail)

  /** a scope: country:JP, region:AU-NSW, settlement_system:TARGET2 */
  def scope: Parser[String] = """[a-z][a-z_]*:[A-Z0-9][A-Z0-9_]*(-[A-Z0-9][A-Z0-9_]*)*""".r

  /** keys, event classes and named calendars; the words of kinds, precisions and labels are names as well */
  def key: Parser[String] = """[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*""".r

  /** the text without the quotes */
  def string: Parser[String] = "\"[^\"]*\"".r ^^ (s => s.substring(1, s.length - 1))

  def period: Parser[TemporalAmount] = number ~ "[DWMQHYBF](?![A-Z])".r ^^ { case n ~ unit => ScheduleSemantics.period(n, unit) }

  // ================= declarations =================

  def ruleFile: Parser[List[AnyRef]] = rep(declaration)

  def declaration: Parser[AnyRef] = ruleDecl | weekendDecl | calendarDecl

  def ruleDecl: Parser[AnyRef] = withText(kw("rule") ~> key ~ (kw("for") ~> key) ~ (kw("in") ~> scopes) ~
    opt(kw("except") ~> scopes) ~ opt(validity) ~ ("=" ~> body) ~ opt(attributes) ~ opt(provenance) <~ ";") ^^ {
    case (key ~ eventClass ~ include ~ exclude ~ validity ~ body ~ attributes ~ citations, text) =>
      val (from, to) = validity.getOrElse((None, None))
      val plain = text.replaceAll("\"[^\"]*\"|#[^\n]*", "")
      RuleCatalog.rule(key, eventClass, include.asJava, exclude.getOrElse(Nil).asJava, from.orNull, to.orNull, body,
        attributes.getOrElse(Nil).asJava, citations.getOrElse(Nil).asJava,
        Reference.findAllIn(plain).map(_.tail).toSet.asJava, Estimated.findFirstIn(plain).isDefined)
  }

  def weekendDecl: Parser[AnyRef] = kw("weekend") ~> scope ~ ("=" ~> weekdaySet) ~ opt(kw("from") ~> isoDate) <~ ";" ^^ {
    case s ~ weekdays ~ from => RuleCatalog.weekend(s, weekdays.asJava, from.orNull)
  }

  def calendarDecl: Parser[AnyRef] = kw("calendar") ~> key ~ ("=" ~> calendar) <~ ";" ^^ {
    case k ~ c => RuleCatalog.calendarDeclaration(k, c)
  }

  /** exactly one of the alternatives will happen: ONE OF {item AS name, ...} */
  def body: Parser[AnyRef] =
    kw("ONE") ~> kw("OF") ~> "{" ~> rep1sep(alternative, ",") <~ "}" ^^ (list => RuleCatalog.oneOf(list.asJava)) |
      exprs

  def alternative: Parser[JMap.Entry[String, Rule]] = item ~ (kw("AS") ~> key) ^^ {
    case i ~ name => JMap.entry(name, RuleCatalog.exprs(List(i).asJava))
  }

  def scopes: Parser[List[String]] = rep1sep(scope, ",")

  def validity: Parser[(Option[LocalDate], Option[LocalDate])] =
    kw("valid") ~> opt(kw("from") ~> isoDate) ~ opt(kw("to") ~> isoDate) ^^ { case from ~ to => (from, to) }

  def isoDate: Parser[LocalDate] = number ~ ("-" ~> number) ~ ("-" ~> number) ^^ { case y ~ m ~ d => LocalDate.of(y, m, d) }

  def attributes: Parser[List[JMap.Entry[String, AnyRef]]] = kw("with") ~> rep1sep(attribute, ",")

  def attribute: Parser[JMap.Entry[String, AnyRef]] =
    kw("kind") ~> "=" ~> "(closed|non_working|short_day|working_day|paid_no_rest|tourist|event|special_session|changed_hours)(?![a-z_])".r ^^
      (k => JMap.entry[String, AnyRef]("kind", k)) |
    kw("session") ~> time ~ opt("-" ~> time) ~ opt(kw("tz") ~> string) ^^ {
      case opens ~ closes ~ tz => RuleCatalog.session(opens, closes.orNull, tz.orNull)
    } |
    kw("precision") ~> "=" ~> (kw("day") | kw("month")) ^^ (p => JMap.entry[String, AnyRef]("precision", p)) |
    kw("note") ~> string ^^ (s => JMap.entry[String, AnyRef]("note", s))

  def time: Parser[String] = number ~ (":" ~> number) ^^ { case h ~ m => RuleCatalog.time(h, m) }

  def provenance: Parser[List[Citation]] = kw("cites") ~> rep1sep(citation, ",")

  def citation: Parser[Citation] =
    string ~ opt(kw("art") ~> string) ~ opt(kw("url") ~> string) ~
      (kw("label") ~> (kw("official") | kw("benchmark") | kw("practice") | kw("secondary"))) ^^ {
      case source ~ article ~ url ~ label => new Citation(source, article.orNull, url.orNull, label)
    }

  // ================= calendars =================

  /** a business day in every term of "&" */
  def calendar: Parser[CalendarSpec] = rep1sep(calTerm, "&") ^^ (terms => RuleCatalog.calendar(terms.asJava))

  /** USD on T+1 does not count, unless for MXN; the scope before the key, which would take its first word */
  def calTerm: Parser[CalendarSpec] =
    scope ~ opt(kw("ignore") ~> kw("at") ~> kw("T") ~> "+" ~> number ~ opt(kw("unless") ~> "{" ~> rep1sep(ccy, ",") <~ "}")) ^^ {
      case s ~ None => new Scope(s, 0, JSet.of())
      case s ~ Some(days ~ unless) => new Scope(s, days, unless.getOrElse(Nil).toSet.asJava)
    } |
    key ^^ (k => new Named(k)) |
    "(" ~> calendar <~ ")"

  /** in an expression one term only, as "&" there is include: ON country:BR, ON usd_eur, ON (a & b) */
  def on: Parser[CalendarSpec] =
    kw("ON") ~> (scope ^^ (s => new Scope(s, 0, JSet.of()): CalendarSpec) | key ^^ (k => new Named(k)) | "(" ~> calendar <~ ")")

  // ================= expressions =================

  def exprs: Parser[Rule] = rep1sep(item, ",") ^^ (items => RuleCatalog.exprs(items.asJava))

  def item: Parser[Expr] = pickedItem | plainItem

  /** 5TH [SPOT : 1Q : ] > EOQ > ...: the item starts with a series or an event, where several dates come from */
  def pickedItem: Parser[Expr] =
    ordinal ~ opt(kw("LAST")) ~ (series | eventName ^^ (n => ScheduleSemantics.event(n))) ~ rep(shift) ~ rest ^^ {
      case n ~ last ~ start ~ moves ~ finish =>
        ScheduleSemantics.pick(n, last.isDefined, finish(RuleCatalog.shifted(start, moves.asJava)))
    }

  def plainItem: Parser[Expr] = expr ~ rest ^^ { case e ~ finish => finish(e) }

  /** the combines and the IF of an item; the condition applies to every date, the shifts after it to the dates it keeps */
  def rest: Parser[Expr => Expr] = rep(combine) ~ opt(ifClause) ^^ { case combines ~ clause => (first: Expr) =>
    RuleCatalog.ruleItem(combines.foldLeft(first)((left, c) => c(left)), clause.orNull)
  }

  def ifClause: Parser[IfClause] = kw("IF") ~> condition ~ rep(shift) ^^ { case c ~ moves => new IfClause(c, moves.asJava) }

  /** A & B includes the dates of B, A \ B excludes them; the shifts after it apply to the result */
  def combine: Parser[Expr => Expr] = ("&" ^^^ true | "\\" ^^^ false) ~ operand ~ rep(shift) ^^ {
    case include ~ o ~ moves => (left: Expr) => RuleCatalog.shifted(ScheduleSemantics.combined(left, include, o.asJava), moves.asJava)
  }

  /** a date, a series or a set of dates */
  def operand: Parser[List[Expr]] = "{" ~> rep1sep(expr, ",") <~ "}" | date ^^ (List(_))

  /** [a : step : b], every day without a step; "]" at the start or "[" at the end leaves that endpoint out */
  def series: Parser[Expr] =
    ("[" ^^^ false | "]" ^^^ true) ~ endpoint ~
      (":" ~> ((period <~ ":") ~ opt(endpoint) ^^ { case p ~ e => (Option(p), e) } | endpoint ^^ (e => (None, Some(e))))) ~
      ("]" ^^^ false | "[" ^^^ true) ^^ {
      case startOut ~ first ~ ((step, last)) ~ endOut => ScheduleSemantics.series(first, step.orNull, last.orNull, startOut, endOut)
    }

  /** an endpoint is one date, so it can't be a series */
  def endpoint: Parser[Expr] = singleDate ~ rep(shift) ^^ { case d ~ moves => RuleCatalog.shifted(d, moves.asJava) }

  /** shifts are applied from left to right */
  def expr: Parser[Expr] = date ~ rep(shift) ^^ { case d ~ moves => RuleCatalog.shifted(d, moves.asJava) }

  def date: Parser[Expr] = series | singleDate

  def shift: Parser[Move] =
    "+" ~> period ~ opt(kw("ENDEND")) ~ opt(on) ^^ { case p ~ endend ~ c => RuleCatalog.plus(p, 1, endend.isDefined, c.orNull) } |
    "-" ~> period ~ opt(kw("ENDEND")) ~ opt(on) ^^ { case p ~ endend ~ c => RuleCatalog.plus(p, -1, endend.isDefined, c.orNull) } |
    ">" ~> (
      kw("IMM") ^^^ RuleCatalog.nextImm() |
      kw("SPOT") ~> opt(pair) ~ opt(on) ^^ { case p ~ c => RuleCatalog.spot(p.orNull, c.orNull) } |
      kw("FIX") ~> opt(pair) ^^ (p => RuleCatalog.fix(p.orNull)) |
      kw("NEXT") ~> opt(ordinal) ~ anchor ^^ { case n ~ a => RuleCatalog.next(n.getOrElse(1), a) } |
      kw("PREV") ~> opt(ordinal) ~ anchor ^^ { case n ~ a => RuleCatalog.previous(n.getOrElse(1), a) } |
      kw("NEAREST") ~> anchor ^^ (a => RuleCatalog.nearestShift(a)) |   // ties go forward
      kw("POSTPONE") ~> roll ~ opt(on) ~ (kw("MAX") ~> period) ~ opt(kw("CUMULATIVE")) ^^ {
        case r ~ c ~ p ~ cumulative => new Postpone(r, c.orNull, p, cumulative.isDefined): Move
      } |
      roll ~ opt(on) ^^ { case r ~ c => RuleCatalog.rollShift(r, c.orNull) } |
      // > 3RD WED IN MONTH, > 2ND LAST BD IN MONTH
      count ~ select ~ (kw("IN") ~> periodWord) ^^ { case (n, fromEnd) ~ a ~ p => RuleCatalog.select(n, fromEnd, a, p) } |
      // > {1 FEB, 1 MAY}: the dates of the set in the year of the date
      "{" ~> rep1sep(annual, ",") <~ "}" ^^ (annuals => RuleCatalog.datesInYear(annuals.asJava))) |
    // {SAT - 1D, TUE WED > PREV MON}: the first entry for the weekday of the date moves it
    "{" ~> rep1sep(observe, ",") <~ "}" ^^ (entries => RuleCatalog.observance(entries.asJava))

  def observe: Parser[JMap.Entry[JSet[String], Move]] = rep1(weekday) ~ (
    "+" ~> period ^^ (p => (w: JSet[String]) => RuleCatalog.observePlus(w, p, 1)) |
    "-" ~> period ^^ (p => (w: JSet[String]) => RuleCatalog.observePlus(w, p, -1)) |
    ">" ~> (
      kw("NEXT") ~> anchor ^^ (a => (w: JSet[String]) => RuleCatalog.observeAnchor(w, a, 1)) |
      kw("PREV") ~> anchor ^^ (a => (w: JSet[String]) => RuleCatalog.observeAnchor(w, a, -1)) |
      kw("NEAREST") ~> anchor ^^ (a => (w: JSet[String]) => RuleCatalog.observeNearest(w, a)) |
      roll ~ opt(on) ^^ { case r ~ c => (w: JSet[String]) => RuleCatalog.observeRoll(w, r, c.orNull) })) ^^ {
    case weekdays ~ entry => entry(weekdays.toSet.asJava)
  }

  /** the count of a selection: from the start, or from the end with LAST */
  def count: Parser[(Int, Boolean)] = ordinal ~ opt(kw("LAST")) ^^ { case n ~ last => (n, last.isDefined) } | kw("LAST") ^^^ ((1, true))

  /** the targets of a selection; an annual date starting with LAST only in braces, as LAST also starts the count */
  def select: Parser[Anchor] = "{" ~> rep1sep(target, ",") <~ "}" ^^ (a => ScheduleSemantics.anyOf(a.asJava)) | chosen

  /** NEXT {MON, FRI}: the nearest of a set */
  def anchor: Parser[Anchor] = "{" ~> rep1sep(target, ",") <~ "}" ^^ (a => ScheduleSemantics.anyOf(a.asJava)) | target

  def target: Parser[Anchor] = chosen |
    kw("LAST") ~> weekday ~ month ^^ { case w ~ m => ScheduleSemantics.annual(ScheduleSemantics.lastWeekday(w, m)) }

  def chosen: Parser[Anchor] =
    weekday ^^ (w => ScheduleSemantics.weekday(w)) |
    kw("BD") ~> opt(on) ^^ (c => RuleCatalog.businessDay(c.orNull)) |
    kw("FD") ^^^ ScheduleSemantics.FIXING_DAY |
    kw("IMM") ^^^ ScheduleSemantics.IMM |
    fixedAnnual ^^ (a => ScheduleSemantics.annual(a)) |
    eventName ^^ (n => ScheduleSemantics.eventAnchor(n)) |
    kw("HOLIDAY") ~> opt(on) ^^ (c => RuleCatalog.holiday(c.orNull)) |
    kw("NONHOLIDAY") ~> opt(on) ^^ (c => RuleCatalog.nonholiday(c.orNull))

  /** a date that is not a series; the annual dates before the dates that also start with a number */
  def singleDate: Parser[Expr] =
    eventName ^^ (n => ScheduleSemantics.event(n)) |
    kw("TOM") ^^^ ScheduleSemantics.plus(ScheduleSemantics.BASE, ScheduleSemantics.period(1, "B")) |
    kw("T") ^^^ ScheduleSemantics.BASE |
    kw("SPOT") ~> opt(pair) ~ opt(on) ^^ { case p ~ c => RuleCatalog.spotDate(p.orNull, c.orNull) } |
    annual ^^ (a => ScheduleSemantics.inYear(a)) |
    number ~ (
      ("-" ~> number) ~ ("-" ~> number) ^^ { case m ~ d => (y: Int) => LocalDate.of(y, m, d) }      // ISO
    | ("." ~> number) ~ ("." ~> number) ^^ { case m ~ y => (d: Int) => LocalDate.of(y, m, d) }      // German
    | ("/" ~> number) ~ ("/" ~> number) ^^ { case d ~ y => (m: Int) => LocalDate.of(y, m, d) }      // US
    ) ^^ { case first ~ toDate => ScheduleSemantics.literal(toDate(first)) }

  /** dates that recur every year */
  def annual: Parser[Annual] = fixedAnnual |
    kw("LAST") ~> weekday ~ month ^^ { case w ~ m => ScheduleSemantics.lastWeekday(w, m) }

  def fixedAnnual: Parser[Annual] =
    number ~ month ~ opt(hijriMethod) ^^ { case d ~ m ~ method => RuleCatalog.monthDay(d, m, method.orNull) } |
    number ~ weekday ~ month ^^ { case n ~ w ~ m => ScheduleSemantics.nthWeekday(n, w, m) } |
    kw("EASTER") ^^^ ScheduleSemantics.EASTER |
    kw("ORTHODOX") ~> kw("EASTER") ^^^ RuleCatalog.ORTHODOX_EASTER |
    (kw("EQUINOX") | kw("SOLSTICE")) ~ month ~ opt(zone) ^^ { case k ~ m ~ z => RuleCatalog.season(k, m, z.orNull) } |
    kw("SOLARTERM") ~> number ~ opt(zone) ^^ { case n ~ z => RuleCatalog.solarTerm(n, z.orNull) } |
    kw("HINDU") ~> key ^^ (k => RuleCatalog.hindu(k))

  /** 1 SHAWWAL BY SIGHTING */
  def hijriMethod: Parser[String] = kw("BY") ~> (kw("UMMALQURA") | kw("SIGHTING") | kw("DIYANET") | kw("FOLLOW") <~ scope)

  /** UTC+9 or an IANA time zone such as "Asia/Tokyo" */
  def zone: Parser[String] = "UTC[+-][0-9]+".r | string

  // ================= conditions =================

  def condition: Parser[Condition] = rep1sep(condAnd, kw("OR")) ^^ {
    case List(c) => c
    case parts => RuleCatalog.anyCondition(parts.asJava)
  }

  def condAnd: Parser[Condition] = rep1sep(condNot, kw("AND")) ^^ {
    case List(c) => c
    case parts => RuleCatalog.allConditions(parts.asJava)
  }

  def condNot: Parser[Condition] = opt(kw("NOT")) ~ condAtom ^^ {
    case negate ~ c => if (negate.isDefined) RuleCatalog.notCondition(c) else c
  }

  def condAtom: Parser[Condition] =
    kw("DAY") ~> number ~ (".." ~> number) ~ opt(month) ^^ { case a ~ b ~ m => RuleCatalog.onDay(a, b, m.orNull) } |   // DAY 8..14 APR
    kw("BETWEEN") ~> kw("HOLIDAYS") ~> opt(on) ^^ (c => RuleCatalog.betweenHolidays(c.orNull)) |   // JP art. 3(3)
    kw("EVERY") ~> period ~ (kw("FROM") ~> number) ^^ { case p ~ n => RuleCatalog.every(p, n) } |   // EVERY 4Y FROM 1965
    kw("HOLIDAY") ~> opt(on) ^^ (c => RuleCatalog.holidayCondition(c.orNull)) |
    "(" ~> condition <~ ")" |
    subject ~ (kw("IS") ~> (
      weekdaySet ^^ (w => (s: Expr) => RuleCatalog.isWeekday(s, w.asJava)) |
      kw("HOLIDAY") ~> opt(on) ^^ (c => (s: Expr) => RuleCatalog.dateIsHoliday(s, c.orNull)))) ^^ { case s ~ make => make(s) } |
    weekdaySet ^^ (w => RuleCatalog.onWeekday(w.asJava))   // the weekday of the date itself

  /** 18 SEP, EASTER + 1D */
  def subject: Parser[Expr] = singleDate ~ rep(("+" ^^^ 1 | "-" ^^^ -1) ~ period) ^^ {
    case d ~ shifts => RuleCatalog.shifted(d, shifts.map { case sign ~ p => RuleCatalog.plus(p, sign, false, null) }.asJava)
  }

  def weekdaySet: Parser[Set[String]] = ("{" ~> rep1sep(weekday, ",") <~ "}" | rep1(weekday)) ^^ (_.toSet)

  /** the result of p with the text it read */
  def withText[T](p: Parser[T]): Parser[(T, String)] = Parser { in =>
    p(in) match {
      case Success(t, next) => Success((t, in.source.subSequence(in.offset, next.offset).toString), next)
      case failure: Failure => failure
      case error: Error => error
    }
  }
}

object RuleFiles {

  /** @references; one regex, so there is no whitespace inside */
  private val Reference = """@[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)*""".r

  /** a Hijri method other than UMMALQURA marks the rule's dates as projections */
  private val Estimated = """\bBY\s+(SIGHTING|DIYANET|FOLLOW)\b""".r

  /**
   * the rule catalog of a rule file
   *
   * @throws IllegalArgumentException on a syntax error, overlapping versions, an unknown or a cyclic reference
   * @throws java.time.DateTimeException on an invalid date
   */
  def parseRules(text: String): RuleCatalog = parseRules(text, null)

  /** the same with announced dates: key -> the dates an authority published */
  def parseRules(text: String, announcements: JMap[String, JList[LocalDate]]): RuleCatalog = {
    val parsers = new RuleFiles
    parsers.parseAll(parsers.ruleFile, text) match {
      case parsers.Success(declarations, _) =>
        new RuleCatalog(declarations.asJava, announcements, (t: String) => schedule(t))
      case failure: parsers.NoSuccess => throw new IllegalArgumentException(failure.toString)
    }
  }

  /** one expression of the rule language */
  def schedule(text: String): Rule = {
    val parsers = new RuleFiles
    parsers.parseAll(parsers.exprs, text) match {
      case parsers.Success(rule, _) => rule
      case failure: parsers.NoSuccess => throw new IllegalArgumentException(failure.toString)
    }
  }
}
