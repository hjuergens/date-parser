package de.juergens

import java.io.File

import de.juergens.text.DateRuleParsers

import scala.collection.JavaConversions._
import scala.io.Source

object FileTesterCompanion {

  def parserMethod[T](parser: scala.util.parsing.combinator.Parsers =
                      new DateRuleParsers, methodName : String = "adjuster") : parser.Parser[T] = {
    val m : AnyRef = parser.getClass.getMethod(methodName).invoke(parser)
    m.asInstanceOf[parser.Parser[T]]
  }

  // NOTE: Must return collection of Array[AnyRef] (NOT Array[Any]).
  def linesOfFile(path:String= "/parsers/adjuster.txt"): java.lang.Iterable[Array[Object]] ={
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

  def getListOfFiles(dir: File):List[File] =
    dir.listFiles.filter(_.isFile).toList

  import scala.util.matching.Regex

  /**
    * http://stackoverflow.com/questions/2637643/how-do-i-list-all-files-in-a-subdirectory-in-scala
    */
  def recursiveListFiles(f: File, r: Regex): Array[File] = {
    val these = f.listFiles
    val good = listFiles(f,r)
    good ++ these.filter(_.isDirectory).flatMap(listFiles(_,r))
  }

  def listFiles(f: File, r: Regex): Array[File] =
    f.listFiles.filter(f => r.findFirstIn(f.getName).isDefined)

}
