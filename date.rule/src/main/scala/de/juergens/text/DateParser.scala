/*
 */

package de.juergens.text

import java.time.{LocalDate => Date}

import scala.util.parsing.combinator._
class DateParser extends JavaTokenParsers with ExtendedRegexParsers {
  def date : Parser[Date] = RegexParser("""(\d{1,2})\.(\d{1,2})\.(\d{2,4})""".r) ^^
    { x => {
      var year       = Integer.parseInt(x.group(3))
      if(year < 100) year+=2000 // TODO
      val month      = Integer.parseInt(x.group(2))
      val dayOfMonth = Integer.parseInt(x.group(1))
      Date.of(year, month, dayOfMonth)
    } }

  assert( parse(date, "23.07.2015").get equals Date.of(2015,7,23) )
}
   