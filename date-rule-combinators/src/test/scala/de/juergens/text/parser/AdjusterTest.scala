package de.juergens.text.parser

/**
 * Created by juergens on 31.05.15.
 */

import java.io.File

import de.juergens.text.{DateRuleParsers, ParserTest, ParserTestCompanion}
import org.junit._
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.testng.Reporter

import scala.jdk.CollectionConverters._
import scala.io.Source



@RunWith(value = classOf[Parameterized])
class AdjusterTest(line: String) extends ParserTest(new DateRuleParsers) {

  @Before
  def before() {
    Reporter.log( s"length=${line.length}")
    Reporter.log("line: " + line)
  }

  @After
  def after() { Reporter.log("line: " + line) }

  @Test(timeout = 1500)
  def test() : Unit =  { test(line) }

  private[text] def test(_line:String) : Unit =  {
    val result = parse("adjuster", _line.toLowerCase)
    assert( result.successful, "parse unsuccessful" )
  }

}

object AdjusterTest extends ParserTestCompanion {

  val textFile = {
    val url = getClass.getResource("/parsers/adjuster.txt")
    new File(url.getFile)
  }

  @org.junit.BeforeClass
  def before() {
    Reporter.log(s"textFile=$textFile")
  }

  @org.junit.AfterClass
  def after() {
    Reporter.log(s"textFile=$textFile")
  }

  private[text] def _lines : Iterator[Array[Object]] = {
    val filteredLines = Source.fromURI {
      textFile.toURI
    }.getLines().map(_.trim)
      .filterNot(_.startsWith("#"))
      .filterNot(_.isEmpty)
      .filterNot(_.forall(_.isWhitespace))
      .filterNot(_.forall(_ == '\u200B'))
    filteredLines.map(Array[Object](_)).iterator
  }

  // NOTE: Must return collection of Array[AnyRef] (NOT Array[Any]).
  @Parameters(name = "{index}: {0}")
  def linesJUnit: java.lang.Iterable[Array[Object]] = _lines.toIterable.asJava

}
