package de.juergens.text

import java.time.temporal.{Temporal, TemporalAdjuster}
import java.time.{LocalDate, Year}

import org.junit.Assert._
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(value = classOf[JUnit4])
class TwoLondonBusinessDaysPriorToThe3rdWednesdayOfTheDeliveryMonthTest
  extends ParserTest(new DateRuleParsers) {

  @Test(timeout = 1500)
  def testLondonBusinessDays() : Unit =  {
    val parseResult = parse("londonBusinessDayUnit", "London business days".toLowerCase)
    assertTrue("", parseResult.successful)
  }
  @Test(timeout = 1500)
  def testPriorTo() : Unit =  {
    val parseResult = parse("afterOrBefore", "prior".toLowerCase)
    assertTrue("", parseResult.successful)
  }
  @Test(timeout = 1500)
  def test3rdWednesday() : Unit =  {
    val parseResult = parse("seekDayOfWeek", "3rd Wednesday".toLowerCase)
    assertTrue("", parseResult.successful)
  }
}
