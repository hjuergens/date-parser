package de.juergens.text

/**
 * Created by juergens on 31.05.15.
 */

import java.time.temporal.TemporalAmount

import org.junit._
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.testng.Reporter
import org.testng.Assert._

import scala.collection.JavaConversions._
import scala.io.Source

// <test name="Test1" junit="true">
//@org.testng.annotations.Test   .Test(name="Test1", junit="true")
@RunWith(value = classOf[Parameterized])
class PeriodDurationTest(line: String) {
  val parser = new DateRuleParsers
  val periodParser : parser.Parser[TemporalAmount] = parser.period
  val durationParser : parser.Parser[TemporalAmount] = parser.duration

  @Before
  def before {
    Reporter.log( s"length=${line.length}")
    Reporter.log("line: " + line)
  }

  @After
  def after { Reporter.log("line: " + line) }

  @Test(timeout = 1000)
  def test() : Unit =  { test(line) }

  private[text] def test(_line:String) : Unit =  {
    val parseResult = parser.parseAll(periodParser | durationParser, _line.toLowerCase)
    assertTrue(parseResult != null, s"Result is NULL! Input line is '${_line.toLowerCase}'.")
    assertTrue(parseResult.successful, s"No success! Input line is '${_line.toLowerCase}'.")
    Reporter.log(parseResult.toString, true)
  }
}

object PeriodDurationTest {

  val textFileURI = getClass.getResource("/period.txt").toURI

  @org.junit.BeforeClass
  def before {
    Reporter.log(s"textFile=$textFileURI")
  }

  @org.junit.AfterClass
  def after {
    Reporter.log(s"textFile=$textFileURI")
  }

  private[text] def _lines : Iterator[Array[Object]] = {
    val filteredLines = Source.fromURI(textFileURI)
      .getLines().map(_.trim)
      .filterNot(_.startsWith("#"))
      .filterNot(_.isEmpty)
      .filterNot(_.forall(_.isWhitespace))
      .filterNot(_.forall(_ == '\u200B'))
    filteredLines.map(Array[Object](_))
  }

  // NOTE: Must return collection of Array[AnyRef] (NOT Array[Any]).
  @Parameters(name = "{index}: {0}")
  def linesJUnit: java.lang.Iterable[Array[Object]] = _lines.toSeq

}
