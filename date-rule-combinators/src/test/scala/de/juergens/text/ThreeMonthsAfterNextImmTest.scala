package de.juergens.text

import java.time.temporal.{Temporal, TemporalAdjuster}
import java.time.{LocalDate, Year}

import org.junit.Assert._
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(value = classOf[JUnit4])
class ThreeMonthsAfterNextImmTest extends ParserTest[TemporalAdjuster](new DateRuleParsers) {
  @Test(timeout = 1500)
  def testThree() : Unit =  {
    val parseResult = parser.parseAll(parserMethod("cardinal"), "three".toLowerCase)
    assertTrue("", parseResult.successful)
  }
  @Test(timeout = 1500)
  def testThreeMonths() : Unit =  {
    val parseResult = parser.parseAll(parserMethod("period"), "three months".toLowerCase)
    assertTrue("", parseResult.successful)
  }
  @Test(timeout = 1500)
  def testThreeMonthsAfter() : Unit =  {
    val parseResult = parser.parseAll(parserMethod("shifter"), "three months after".toLowerCase)
    assertTrue("", parseResult.successful)
  }
  @Test(timeout = 1500)
  def testImm() : Unit =  {
    val parserTest = new ParserTest(new TermParsers{})
    val parseResult = parserTest.parser.parseAll(parserTest.parserMethod("imm"), "imm".toLowerCase)
    assertTrue("", parseResult.successful)
  }
  @Test(timeout = 1500)
  def testNextImm() : Unit =  {
    val parserTest = new ParserTest[TemporalAdjuster](new TermParsers{})
    val parseResult = parserTest.parser.parseAll(parserTest.parserMethod("imm"), "next imm".toLowerCase)
    assertTrue("", parseResult.successful)

    assertEquals(LocalDate.parse("2016-06-15"), parseResult.get.adjustInto(LocalDate.parse("2016-05-15")))
    assertEquals(LocalDate.parse("2016-09-21"), parseResult.get.adjustInto(LocalDate.parse("2016-06-15")))
  }
  @Test(timeout = 1500)
  def test() : Unit =  {
    object CunjunctionParsers extends DateRuleParsers with TermParsers {

      override def year4: Parser[Year] = super.year4

      def enTotal : Parser[TemporalAdjuster] = shifter ~ imm ^^
        { case ~(rhs,lhs) => new TemporalAdjuster{
          override def adjustInto(tp: Temporal): Temporal = rhs.adjustInto(lhs.adjustInto(tp))
        } }
    }
    val parseResult = CunjunctionParsers.parseAll(CunjunctionParsers.enTotal, "three months after next imm".toLowerCase)
    assertTrue("fail to parse 'three months after next imm'", parseResult.successful)

    assertEquals(LocalDate.parse("2016-09-15"), parseResult.get.adjustInto(LocalDate.parse("2016-06-14")))
    assertEquals(LocalDate.parse("2016-12-21"), parseResult.get.adjustInto(LocalDate.parse("2016-06-15")))
    assertEquals(LocalDate.parse("2016-12-21"), parseResult.get.adjustInto(LocalDate.parse("2016-06-16")))
  }
}
