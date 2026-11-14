/*
 * Copyright 2015 Hartmut Jürgens
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.juergens.text

import de.juergens.util.{Cardinal, Ordinal}

import scala.util.parsing.combinator.RegexParsers

trait CardinalParsers extends RegexParsers {
  private def numerical : Parser[Cardinal] =
    """(\d+)""".r ^^
      { Cardinal.fromString }

  private def textual : Parser[Cardinal] =
    ( "one" | "two" | "three" | "four" | "five" | "six" | "seven" | "eight" | "nine" | "ten" | "eleven" | "twelve") ^^
      { Cardinal.string2Long.andThen(Cardinal.apply _) }

  def cardinal : Parser[Cardinal] = textual | numerical
}

trait OrdinalParsers  extends RegexParsers with ExtendedRegexParsers {
  val regExp = """(\d*1)st|(\d*2)nd|(\d*3)rd|(\d*[4,5,6,7,8,9,0])th|(1[1,2,3])th""".r


  private def textual = ("first" | "eleventh" | "second" | "twelfth" | "third" | "thirteenth"| "fourth" |
    "fourteenth" | "fifth" | "fifteenth" | "sixth" | "sixteenth" | "seventh " | "seventeenth" |
    "eighth" | "eighteenth" | "ninth" | "nineteenth" | "tenth" | "twelfth" | "twelfe" | "twelveth" | "twentieth") ^^
    {
      Ordinal.string2Int.andThen(Ordinal.apply _)
    }

  @SuppressWarnings(Array("all"))
  private def ordinalNumbers : Parser[Ordinal] = RegexParser(regExp) ^^
    { case m =>
      val matchingStr = (1 to m.groupCount).map(m.group).filterNot(_ == null).head
      Ordinal(Integer.parseInt(matchingStr))
    }

  private def numerical :  Parser[Ordinal] = RegexParser("""(\d+)\.""".r) ^^
    { case m => Ordinal(Integer.parseInt(m.group(1))) }


  assert( parseAll(ordinalNumbers,
    "1st 2nd 3rd 4th 5th 6th 7th 8th 9th 10th 11th 12th 13th 14th 15th 16th 17th 18th 19th 20th 21st 22nd 23rd" +
      "24th 25th 26th 27th 28th 29th 30th 101st 102nd 103rd 104th 105th 106th 107th 108th 109th 110th").isEmpty
  )

  import scala.language.postfixOps
  def ordinal : Parser[Ordinal] = ("the"?) ~ (textual | numerical | ordinalNumbers) ^^
  {case _ ~ ord => ord}
}

trait NumberParsers extends RegexParsers with CardinalParsers with OrdinalParsers
