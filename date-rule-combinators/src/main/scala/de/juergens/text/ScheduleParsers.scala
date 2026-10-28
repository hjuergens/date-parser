package de.juergens.text

import java.time.{LocalDate, Period}

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

  def period: Parser[Period] = number ~ "[DWMQY]".r ^^ {
    case n ~ "D" => Period.ofDays(n)
    case n ~ "W" => Period.ofWeeks(n)
    case n ~ "M" => Period.ofMonths(n)
    case n ~ "Q" => Period.ofMonths(3 * n)
    case n ~ "Y" => Period.ofYears(n)
  }

  /** periods are applied from left to right */
  def expr: Parser[LocalDate] = date ~ rep(("+" | "-") ~ period) ^^ {
    case start ~ shifts => shifts.foldLeft(start) {
      case (d, "+" ~ p) => d.plus(p)
      case (d, _ ~ p) => d.minus(p)
    }
  }

  def range: Parser[List[LocalDate]] = ("(" ~> expr) ~ (":" ~> period) ~ (":" ~> expr <~ ")") ^^ {
    case first ~ step ~ last =>
      require(!step.isZero && !step.isNegative, s"step must be positive: $step")
      Iterator.from(0).map(k => first.plus(step.multipliedBy(k))).takeWhile(!_.isAfter(last)).toList
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
