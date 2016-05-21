package de.juergens.text

import java.io.File
import java.time.temporal.TemporalAdjuster

import scala.util.parsing.combinator.JavaTokenParsers

/**
  * Created by juergens on 15.05.16.
  */
class ParserTest(val parser:JavaTokenParsers, private val methodName: String) {
  type P = parser.Parser[TemporalAdjuster]
  def parserMethod(methodName : String) : P = {
    val m : AnyRef = parser.getClass.getMethod(methodName).invoke(parser)
    m.asInstanceOf[P]
  }
  val parserMethodInstance : parser.Parser[TemporalAdjuster] = parserMethod(methodName)
}

trait ParserTestCompanion {
  val textFile : File
}
