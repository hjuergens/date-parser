/*
 */

package de.juergens.text

import java.time.{LocalDate => Date}

import scala.util.parsing.combinator._
class DateParser extends JavaTokenParsers {
//   def date : Parser[Date] = """(\d){1,2}\.(\d{1,2})\.(\d+){2,4}""".r ^^ => {t:(Int,Int,Int) => Date.of()}

}
   