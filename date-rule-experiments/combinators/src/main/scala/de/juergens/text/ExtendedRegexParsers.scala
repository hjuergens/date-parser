package de.juergens.text

import scala.util.matching.Regex
import scala.util.parsing.combinator.RegexParsers

/**
 * Helper trait
 */
trait ExtendedRegexParsers {
  self: RegexParsers =>

  /**
   * 
   * @param r a regular expression
   * @return
   */
  case class RegexParser(r: Regex) extends Parser[Regex.Match] {
    def apply(in: Input) = {
      val source = in.source
      val offset = in.offset
      val start = handleWhiteSpace(source, offset)
      r findPrefixMatchOf (source.subSequence(start, source.length)) match {
        case Some(matched) =>
          Success(matched, in.drop(start + matched.end - offset))
        case None =>
          Failure("string matching regex `"+r+"' expected but `"+in.first+"' found", in.drop(start - offset))
      }
    }
  }

}
