package de.juergens.text

/**
 * Created by juergens on 31.05.15.
 */

import java.time.chrono.ChronoPeriod
import java.time.temporal.TemporalAmount
import org.junit._
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import scala.collection.JavaConversions._
import scala.io.Source

@RunWith(value = classOf[Parameterized])
class PeriodTest(line: String) {
  val parser = new DateRuleParser
  val periodParser : parser.Parser[TemporalAmount] = parser.period

  @Before
  def before {
    println( s"length=${line.length}")
    println("line: " + line)
  }

  @After
  def after { println("line: " + line) }

  @Test(timeout = 1000)
  def test() : Unit =  { test(line) }

  private[text] def test(_line:String) : Unit =  {
    parser.parseAll(periodParser, _line.toLowerCase).get
  }
}

object PeriodTest {

  val textFileURI = getClass.getResource("/period.txt").toURI

  @org.junit.BeforeClass
  def before {
    println(s"textFile=$textFileURI")
  }

  @org.junit.AfterClass
  def after {
    println(s"textFile=$textFileURI")
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
