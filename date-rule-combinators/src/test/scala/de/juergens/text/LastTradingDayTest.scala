package de.juergens.text

import java.time.temporal.TemporalAdjuster

import de.juergens.text.FileTesterCompanion.{linesOfFile, parserMethod}
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.junit.{Ignore, Test}
import org.testng.Reporter

import scala.util.parsing.combinator.JavaTokenParsers

@Ignore
@RunWith(value = classOf[Parameterized])
class LastTradingDayTest(line : String) {

  val parser : JavaTokenParsers = new DateRuleParsers

  val method = parserMethod[TemporalAdjuster](parser, "seek3")

  @Test(timeout = 1500)
  def test() : Unit =  {
    if(line.contains("???")) return
    val parsResult = parser.parseAll[TemporalAdjuster](method, line.toLowerCase)
    import org.junit.Assert._
    assertTrue(parsResult.toString, parsResult.successful)
    Reporter.log(parsResult.toString)
  }
}

object LastTradingDayTest {
  import java.lang.{Iterable => JIterable}

  @Parameters(name = "{index}: {0}")
  def lines: JIterable[Array[Object]] = linesOfFile("/last_trading_day.txt")

}
