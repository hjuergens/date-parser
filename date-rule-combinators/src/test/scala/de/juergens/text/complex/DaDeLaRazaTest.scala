package de.juergens.text.complex

import java.time._
import java.time.temporal.{ChronoField, TemporalAdjuster}

import de.juergens.text.{DateRuleParsers, ParserTest}
import org.junit.Assert._
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
  * On March 10, 2000, law 19,668[15] moved the Saint Peter and Saint Paul, Columbus Day and Corpus Christi holidays to
  * the preceding Monday, if they were to fall on a Tuesday, Wednesday or Thursday, or to the following Monday, if they
  * were to fall on a Friday. Additionally, the designation of Columbus Day was changed from Aniversario del
  * Descubrimiento de América to Día del Descubrimiento de Dos Mundos ("Discovery of Two Worlds' Day"), an obvious
  * corruption from the then-recent ubiquitous "Encuentro de dos mundos" ("The Meeting of Two Worlds") slogan.
  * [wikipedia]
  **/
@RunWith(value = classOf[JUnit4])
class DaDeLaRazaTest
  extends ParserTest(new DateRuleParsers) {

  val twelvesOfOctober = MonthDay.of(Month.OCTOBER,12)

  @Test(timeout = 1500)
  def testFourth() : Unit =  {
    val parseResult = parse("ordinal", "Twelfth")
    assertTrue("", parseResult.successful)
  }

  @Test(timeout = 1500)
  def testOctober() : Unit =  {
    val parseResult = parse("monthName", "October")
    assertTrue("", parseResult.successful)
    assertTrue("", parseResult.get.isInstanceOf[Month])
  }

  @Test(timeout = 1500)
  def testTwelfthOfOctober() : Unit =  {
    val text = "twelfth of October".toLowerCase
    val p = new DateRuleParsers
    val parseResult = p.parseAll(p.monthDay, text)
    assertTrue("", parseResult.successful)
  }

  @Test(timeout = 1500)
  def testTheTwelfthTwelfthOfOctober() : Unit =  {
    val text = "the Twelfth of October".toLowerCase
    val p = new DateRuleParsers
    val parseResult = p.parseAll(p.monthDay, text)
    assertTrue("", parseResult.successful)
    assertEquals( Month.OCTOBER.getValue,
      parseResult.get.queryFrom(LocalDate.now()).get(ChronoField.MONTH_OF_YEAR) )
    assertEquals( 12,
      parseResult.get.queryFrom(LocalDate.now()).get(ChronoField.DAY_OF_MONTH) )
    assertEquals( LocalDate.parse("2016-10-12"),
      parseResult.get.queryFrom(Year.of(2016).atMonth(Month.OCTOBER).atDay(10)) )
  }

  /**
    * move to preceding Monday, if they were to fall on a Tuesday, Wednesday or Thursday, or to the following Monday, if they
    * were to fall on a Friday
    * Mon	Oct 11	2010	Columbus Day	National holiday
    * Mon	Oct 10	2011	Columbus Day	National holiday
    * Sat	Oct 12	2013	Columbus Day	National holiday
    * Sun	Oct 12	2014	Columbus Day	National holiday
    * Mon	Oct 12	2015	Columbus Day	National holiday
    * Mon	Oct 10	2016	Columbus Day	National holiday
    * Mon	Oct 9	  2017	Columbus Day	National holiday
    * Mon	Oct 15	2018	Columbus Day	National holiday
    * Sat	Oct 12	2019	Columbus Day	National holiday
    * Mon	Oct 12	2020	Columbus Day	National holiday
    */
  @Test(timeout = 1500)
  def testIfOctober12IsATuesdayWednesdayOrThursdayItIsObservedOnPrecedingMonday() : Unit =  {
    val rule = "If October 12 is a Tuesday, Wednesday or Thursday, it is observed on preceding Monday"
    val parseResult = parse[TemporalAdjuster]("observe", rule)
    assertTrue("parse error", parseResult.successful)

    val columbusHoliday = parseResult.get

    val holiday2010 = columbusHoliday.adjustInto( twelvesOfOctober.atYear(2010) )
    assertEquals( Year.of(2010).atMonth(Month.OCTOBER).atDay(11), holiday2010 )
    assertEquals( DayOfWeek.MONDAY.getValue, holiday2010.get(ChronoField.DAY_OF_WEEK) )


    val columbusDayIn2011 = LocalDate.parse("2011-10-12")
    assertEquals( DayOfWeek.MONDAY.getValue,
      parseResult.get.adjustInto(columbusDayIn2011).get(ChronoField.DAY_OF_WEEK) )

    val columbusDayIn2013 = LocalDate.parse("2013-10-12")
    assertEquals( DayOfWeek.SATURDAY.getValue,
      parseResult.get.adjustInto(columbusDayIn2013).get(ChronoField.DAY_OF_WEEK) )

    val columbusDayIn2014 = LocalDate.parse("2014-10-12")
    assertEquals( DayOfWeek.SUNDAY.getValue,
      parseResult.get.adjustInto(columbusDayIn2014).get(ChronoField.DAY_OF_WEEK) )

    val columbusDayIn2015 = LocalDate.parse("2015-10-12")
    assertEquals( DayOfWeek.MONDAY.getValue,
      parseResult.get.adjustInto(columbusDayIn2015).get(ChronoField.DAY_OF_WEEK) )

    val columbusDayIn2016 = LocalDate.parse("2016-10-12")
    assertEquals( DayOfWeek.MONDAY.getValue,
      parseResult.get.adjustInto(columbusDayIn2016).get(ChronoField.DAY_OF_WEEK) )

    val columbusDayIn2017 = LocalDate.parse("2017-10-12")
    assertEquals( DayOfWeek.MONDAY.getValue,
      parseResult.get.adjustInto(columbusDayIn2017).get(ChronoField.DAY_OF_WEEK) )

    val columbusDayIn2019 = LocalDate.parse("2019-10-12")
    assertEquals( DayOfWeek.SATURDAY.getValue,
      parseResult.get.adjustInto(columbusDayIn2019).get(ChronoField.DAY_OF_WEEK) )

    val columbusDayIn2020 = LocalDate.parse("2020-10-12")
    assertEquals( DayOfWeek.MONDAY.getValue,
      parseResult.get.adjustInto(columbusDayIn2020).get(ChronoField.DAY_OF_WEEK) )
  }

  /**
    * move to preceding Monday, if they were to fall on a Tuesday, Wednesday or Thursday, or to the following Monday, if they
    * were to fall on a Friday
    * Mon	Oct 15	2012	Columbus Day	National holiday
    * Sat	Oct 12	2013	Columbus Day	National holiday
    * Sun	Oct 12	2014	Columbus Day	National holiday
    * Mon	Oct 12	2015	Columbus Day	National holiday
    * Mon	Oct 15	2018	Columbus Day	National holiday
    * Sat	Oct 12	2019	Columbus Day	National holiday
    * Mon	Oct 12	2020	Columbus Day	National holiday
    */
  @Test(timeout = 1500)
  def testIfOctober12IsAFridayItIsObservedOnFollowingMonday() : Unit =  {
    val rule = "If October 12 is a Friday, it is observed on following Monday"
    val parseResult = parse[TemporalAdjuster]("observe", rule)
    assertTrue("", parseResult.successful)

    val columbusDayIn2012 = LocalDate.parse("2012-10-12") // FRIDAY
    assertEquals( DayOfWeek.MONDAY.getValue,
      parseResult.get.adjustInto(columbusDayIn2012).get(ChronoField.DAY_OF_WEEK) )

    val columbusDayIn2013 = LocalDate.parse("2013-10-12") // SUNDAY
    assertEquals( DayOfWeek.SATURDAY.getValue,
      parseResult.get.adjustInto(columbusDayIn2013).get(ChronoField.DAY_OF_WEEK) )

    val columbusDayIn2014 = LocalDate.parse("2014-10-12") // SUNDAY
    assertEquals( DayOfWeek.SUNDAY.getValue,
      parseResult.get.adjustInto(columbusDayIn2014).get(ChronoField.DAY_OF_WEEK) )

    val columbusDayIn2015 = LocalDate.parse("2015-10-12") // MONDAY
    assertEquals( DayOfWeek.MONDAY.getValue,
      parseResult.get.adjustInto(columbusDayIn2015).get(ChronoField.DAY_OF_WEEK) )

    val columbusDayIn2018 = LocalDate.parse("2018-10-12")
    assertEquals( DayOfWeek.MONDAY.getValue,
      parseResult.get.adjustInto(columbusDayIn2018).get(ChronoField.DAY_OF_WEEK) )

    val columbusDayIn2019 = LocalDate.parse("2019-10-12")
    assertEquals( DayOfWeek.SATURDAY.getValue,
      parseResult.get.adjustInto(columbusDayIn2019).get(ChronoField.DAY_OF_WEEK) )

    val columbusDayIn2020 = LocalDate.parse("2020-10-12")
    assertEquals( DayOfWeek.MONDAY.getValue,
      parseResult.get.adjustInto(columbusDayIn2020).get(ChronoField.DAY_OF_WEEK) )
  }

}
