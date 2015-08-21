package de.juergens.text

import scala.util.matching.Regex
import scala.util.parsing.combinator.RegexParsers

/**
 * Helper trait
 */
trait ExtendedRegexParsers {
  self: RegexParsers =>

  /**
   * Enrich set of delimiters with ?~ for optional keywords
   * @param p a parser
   * @tparam T a type
   */
  implicit protected class ParserExtension[T](val p: Parser[T]) {
    def ?~[U](q: => Parser[U]): Parser[Option[T] ~ U] = {
      ((p ^^ Some.apply) ~ q) | (success(None) ~ q)
    }
  }

  /**
   * 
   * @param r
   * @return
   */
  protected case class RegexParser(r: Regex) extends Parser[Regex.Match] {
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
