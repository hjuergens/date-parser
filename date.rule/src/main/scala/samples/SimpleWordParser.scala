/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package samples

import scala.util.parsing.combinator._

object SimpleWordParser extends RegexParsers {

  def word: Parser[String] = """[a-z]+""".r ^^ {
    _.toString
  }
}
