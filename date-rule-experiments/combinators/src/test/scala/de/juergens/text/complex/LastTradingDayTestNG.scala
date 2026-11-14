package de.juergens.text.complex

import java.time.temporal.TemporalAdjuster

import de.juergens.FileTesterCompanion.linesOfFile
import de.juergens.text.DateRuleParsers
import org.testng.AssertJUnit._
import org.testng.Reporter
import org.testng.annotations.{DataProvider, Parameters, Test}

import scala.jdk.CollectionConverters._

@Test(groups = Array { "adjuster"  })
class LastTradingDayTestNG {

  val parser = new DateRuleParsers

  val parserMethod : parser.Parser[TemporalAdjuster] = {
    val m : AnyRef = parser.getClass.getMethod("adjuster").invoke(parser)
    m.asInstanceOf[parser.Parser[TemporalAdjuster]]
  }

  @Parameters(value=Array{"line"})
  @Test(dataProvider = "lines in text file", timeOut = 1000)
  def testSeek3(line: String) {
    if(line.contains("???")) return
    val parsResult = parser.parseAll(parserMethod, line.toLowerCase)
    assertTrue(parsResult.toString, parsResult.successful)
    Reporter.log(parsResult.toString)
  }

  @Parameters(value=Array{"line"})
  @Test(dataProvider = "lines in text file", timeOut = 1000)
  def testAdjuster(line: String) {
    if(line.contains("???")) return
    val parsResult = parser.parseAll(parser.adjuster, line.toLowerCase)
    assertTrue(parsResult.toString, parsResult.successful)
    Reporter.log(parsResult.toString)
  }

}

object LastTradingDayTestNG {
  import java.util.{Iterator => JIterator}

  @DataProvider(name = "lines in text file", parallel = true)
  def lines: JIterator[Array[Object]] = linesOfFile("/complex/last_trading_day.txt").iterator.asJava
}
