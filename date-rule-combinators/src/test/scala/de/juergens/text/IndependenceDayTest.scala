package de.juergens.text

import java.time.temporal.{ChronoField, TemporalAdjuster}
import java.time.{DayOfWeek, LocalDate, Month, Year}

import de.juergens.util.Ordinal
import org.junit.Assert._
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
  * "the Fourth of July"
  * If July 4 is a Saturday, it is observed on Friday, July 3. If July 4 is a Sunday,
  * it is observed on Monday, July 5.
  */
@RunWith(value = classOf[JUnit4])
class IndependenceDayTest
  extends ParserTest[TemporalAdjuster](new DateRuleParsers) {

  @Test(timeout = 1500)
  def testFourth() : Unit =  {
    val parseResult = parse("ordinal", "Fourth".toLowerCase)
    assertTrue("", parseResult.successful)
    assertTrue("", parseResult.get.isInstanceOf[Ordinal]) // TODO fruitless
  }

  @Test(timeout = 1500)
  def testJuly() : Unit =  {
    val parseResult = parse("monthName", "July".toLowerCase)
    assertTrue("", parseResult.successful)
    assertTrue("", parseResult.get.isInstanceOf[Month])
  }

  @Test(timeout = 1500)
  def testFourthOfJuly() : Unit =  {
    val parseResult = parse("dayOf", "Fourth of July".toLowerCase)
    assertTrue("", parseResult.successful)
  }

  @Test(timeout = 1500)
  def testTheFourthOfJuly() : Unit =  {
    val parseResult = parse("dayOf", "the Fourth of July".toLowerCase)
    assertTrue("", parseResult.successful)
    assertEquals( Month.JULY.getValue,
      parseResult.get.adjustInto(LocalDate.now()).get(ChronoField.MONTH_OF_YEAR) )
    assertEquals( 4,
      parseResult.get.adjustInto(LocalDate.now()).get(ChronoField.DAY_OF_MONTH) )
    assertEquals( LocalDate.parse("1776-07-04"),
      parseResult.get.adjustInto(Year.of(1776).atMonth(Month.JULY).atDay(4)) )
  }

  /**
    * preponed
    * Fri	Jul 3	2015	Independence Day observed
    * Fri	Jul 3	2020	Independence Day observed
    */
  @Test(timeout = 1500)
  def testIfJuly4IsASaturdayItIsObservedOnFriday() : Unit =  {
    val parseResult = parse("observe", "If July 4 is a Saturday, it is observed on Friday".toLowerCase)
    assertTrue("", parseResult.successful)
    val fourthOfJulyIn2015 = LocalDate.parse("2015-07-04")
    assertEquals( DayOfWeek.FRIDAY.getValue,
      parseResult.get.adjustInto(fourthOfJulyIn2015).get(ChronoField.DAY_OF_WEEK) )
  }

  /**
    * postponed
    * Mon	Jul 5	2010	Independence Day observed
    */
  @Test(timeout = 1500)
  def testIfJuly4ISASundayItIsObservedOnMonday() : Unit =  {
    val parseResult = parse("observe", "If July 4 is a Sunday, it is observed on Monday".toLowerCase)
    assertTrue("", parseResult.successful)
    val fourthOfJulyIn2016 = LocalDate.parse("2016-07-04")
    assertEquals( DayOfWeek.MONDAY.getValue,
      parseResult.get.adjustInto(fourthOfJulyIn2016).get(ChronoField.DAY_OF_WEEK) )
  }
}
