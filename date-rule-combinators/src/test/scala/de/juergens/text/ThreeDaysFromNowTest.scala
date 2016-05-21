package de.juergens.text

import java.time.temporal.{Temporal, TemporalAdjuster}
import java.time._

import org.junit.Assert._
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(value = classOf[JUnit4])
class ThreeDaysFromNowTest extends ParserTest(new DateRuleParsers, "adjuster") {
  private val clock : Clock = Clock.system(ZoneId.systemDefault())  // dependency inject

  @Test(timeout = 1500)
  def testThree() : Unit =  {
    val parseResult = parser.parseAll(parserMethod("cardinal"), "three".toLowerCase)
    assertTrue("", parseResult.successful)
  }
  @Test(timeout = 1500)
  def testThreeDays() : Unit =  {
    val parseResult = parser.parseAll(parserMethod("period"), "three days".toLowerCase)
    assertTrue("", parseResult.successful)
  }
  @Test(timeout = 1500)
  def testThreeDays2() : Unit =  {
    val parseResult = parser.parseAll(parserMethod("addSubtract"), "three days after".toLowerCase)
    assertTrue("", parseResult.successful)
  }
  @Test(timeout = 1500)
  def testNow() : Unit =  {
    val parseResult = parser.parseAll(parserMethod("tomorrowYesterdayToday"), "now".toLowerCase)
    assertTrue("", parseResult.successful)

    assertEquals(LocalDate.now(clock), parseResult.get.adjustInto(LocalDate.parse("2016-06-14")))
  }
  @Test(timeout = 1500)
  def testFromNow() : Unit =  {
    val parseResult = parser.parseAll(parserMethod("tomorrowYesterdayToday"), "from now".toLowerCase)
    assertTrue("", parseResult.successful)
  }
  @Test(timeout = 1500)
  def testThreeDaysFrom() : Unit =  {
    val parseResult = parser.parseAll(parserMethod("addSubtract"), "three days from".toLowerCase)
    assertTrue("", parseResult.successful)
  }
  @Test(timeout = 1500)
  def testThreeDaysFromNow() : Unit =  {
    object CunjunctionParsers extends DateRuleParsers {

      def enTotal : Parser[TemporalAdjuster] = addSubtract ~ now ^^
        { case ~(rhs,lhs) => new TemporalAdjuster{
          override def adjustInto(tp: Temporal): Temporal = rhs.adjustInto(lhs.adjustInto(tp))
        } }
    }
    val parseResult = CunjunctionParsers.parseAll(CunjunctionParsers.enTotal, "three days from now".toLowerCase)
    assertTrue("fail to parse 'three days from now'", parseResult.successful)

    val threeDayslater = Period.ofDays(3).addTo(LocalDate.now(clock))
    assertEquals(threeDayslater, parseResult.get.adjustInto(LocalDate.parse("2016-06-14")))
  }
}
