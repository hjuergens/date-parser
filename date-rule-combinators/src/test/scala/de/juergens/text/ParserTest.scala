package de.juergens.text

import java.io.File

import scala.util.parsing.combinator.JavaTokenParsers

/**
  * Created by juergens on 15.05.16.
  */
class ParserTest[Q<:JavaTokenParsers](val parser:Q) {

  def parserMethod[T](methodName : String) : parser.Parser[T] = {
    val m : AnyRef = parser.getClass.getMethod(methodName).invoke(parser)
    m.asInstanceOf[parser.Parser[T]]
  }

  def parse[T](parserName:String, text:String) :parser.ParseResult[T] =
    parser.parseAll(parserMethod(parserName), text.toLowerCase)

  implicit class TextParser[T](parserName: String) {
    def parse(text: String) :parser.ParseResult[T] = ParserTest.this.parse[T](parserName, text)
  }
}

trait ParserTestCompanion {
  val textFile : File
}
