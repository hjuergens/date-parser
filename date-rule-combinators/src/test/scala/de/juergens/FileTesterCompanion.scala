package de.juergens

import java.io.File

import de.juergens.text.DateRuleParsers

import scala.collection.JavaConversions._
import scala.io.Source

object FileTesterCompanion {

  def parserMethod[T](parser: scala.util.parsing.combinator.Parsers = new DateRuleParsers, methodName : String = "adjuster") : parser.Parser[T] = {
    val m : AnyRef = parser.getClass.getMethod(methodName).invoke(parser)
    m.asInstanceOf[parser.Parser[T]]
  }

  // NOTE: Must return collection of Array[AnyRef] (NOT Array[Any]).
  def linesOfFile(path:String= "/adjuster.txt"): java.lang.Iterable[Array[Object]] ={
    val textFile = {
      val url = getClass.getResource(path)
      new File(url.getFile)
    }
    val filteredLines = Source.fromURI {
      textFile.toURI
    }.getLines().map(_.trim).toSeq.takeWhile(!_.equals("!END"))
      .filterNot(_.startsWith("#"))
      .filterNot(_.isEmpty)
      .filterNot(_.forall(_.isWhitespace))
      .filterNot(_.forall(_ == '\u200B'))

    filteredLines.map(Array[Object](_))
  }
}
