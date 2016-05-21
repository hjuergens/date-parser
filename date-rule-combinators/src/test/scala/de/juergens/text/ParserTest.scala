package de.juergens.text

import java.io.File
import java.time.temporal.TemporalAdjuster

import scala.util.parsing.combinator.JavaTokenParsers

/**
  * Created by juergens on 15.05.16.
  */
class ParserTest(val parser:JavaTokenParsers) {
  type P = parser.Parser[TemporalAdjuster]

  def parserMethod(methodName : String) : P = {
    val m : AnyRef = parser.getClass.getMethod(methodName).invoke(parser)
    m.asInstanceOf[P]
  }

  def parse(parserName:String, text:String) =
    parser.parseAll(parserMethod(parserName), text.toLowerCase)
}

trait ParserTestCompanion {
  val textFile : File
}
