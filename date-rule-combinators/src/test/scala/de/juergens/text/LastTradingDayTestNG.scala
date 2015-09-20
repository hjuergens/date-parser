package de.juergens.text

import java.time.temporal.TemporalAdjuster

import de.juergens.FileTesterCompanion.linesOfFile
import org.testng.AssertJUnit._
import org.testng.Reporter
import org.testng.annotations.{Parameters, DataProvider, Test}

@Test(groups = Array { "seek"  })
class LastTradingDayTestNG {

  val parser = new DateRuleParsers

  val parserMethod : parser.Parser[TemporalAdjuster] = {
    val m : AnyRef = parser.getClass.getMethod("seek3").invoke(parser)
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
  def lines: JIterator[Array[Object]] = linesOfFile("/last_trading_day.txt").iterator()
}
