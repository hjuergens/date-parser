package de.juergens.text

import java.util.Locale

import com.ibm.icu.text.RuleBasedNumberFormat
import de.juergens.util.{Cardinal, Ordinal}

import scala.util.matching.Regex
import scala.util.parsing.combinator.{RegexParsers, JavaTokenParsers}

trait CardinalParsers extends RegexParsers {
  private def numerical : Parser[Cardinal] =
    """(\d+)""".r ^^
      { Cardinal.fromString }

  private def textual : Parser[Cardinal] =
    ( "one" | "two" | "three" | "four" | "five" | "six" | "seven" | "eight" | "nine" | "ten" | "eleven" | "twelve") ^^
      { Cardinal.fromString }

  def cardinal : Parser[Cardinal] = textual | numerical
}

trait OrdinalParser  extends RegexParsers with ExtendedRegexParsers {
  val regExp = """(\d*1)st|(\d*2)nd|(\d*3)rd|(\d*[4,5,6,7,8,9,0])th|(1[1,2,3])th""".r
  val format = new RuleBasedNumberFormat(Locale.US, RuleBasedNumberFormat.ORDINAL)

  private def textual = ("first" | "eleventh" | "second" | "twelfth" | "third" | "thirteenth"| "fourth" |
    "fourteenth" | "fifth" | "fifteenth" | "sixth" | "sixteenth" | "seventh " | "seventeenth" |
    "eighth" | "eighteenth" | "ninth" | "nineteenth" | "tenth" | "twentieth") ^^
    { Ordinal.fromString }

  private def ordinalNumbers : Parser[Ordinal] = RegexParser(regExp) ^^
    { case m => Ordinal(Integer.parseInt(m.group(1))) }

  private def numerical :  Parser[Ordinal] = RegexParser("""(\d+)\.""".r) ^^
    { case m => Ordinal(Integer.parseInt(m.group(1))) }


  assert( parseAll(ordinalNumbers,
    "1st 2nd 3rd 4th 5th 6th 7th 8th 9th 10th 11th 12th 13th 14th 15th 16th 17th 18th 19th 20th 21st 22nd 23rd 24th 25th 26th 27th 28th 29th 30th 101st 102nd 103rd 104th 105th 106th 107th 108th 109th 110th").isEmpty
  )

  def ordinal : Parser[Ordinal] = ordinalNumbers | textual | numerical
}

trait NumberParser extends RegexParsers with CardinalParsers with OrdinalParser
