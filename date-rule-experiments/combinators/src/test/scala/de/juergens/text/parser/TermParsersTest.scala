package de.juergens.text.parser

import java.time.{LocalDate, Period, Year}

import de.juergens.text.TermParsers
import org.junit.Assert._
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(value = classOf[JUnit4])
class TermParsersTest extends TermParsers {

  @Test
  def testDate() :Unit ={
    val result = parseAll(isoDate, "2015-Aug-20")
    assertTrue(result.toString, result.successful)
  }

  @Test
  def testInfinityPeriod() :Unit = {
    val result = parseAll(periodTerm, "Infinity")
    assertTrue(result.toString, result.successful)
  }

  @Test
  def testStandardPeriod() :Unit = {
    {
      val result = parseAll(standardPeriod, "3M")
      assertTrue(result.toString, result.successful)
    }
    {
      val result = parseAll(standardPeriod, "7Y")
      assertTrue(result.toString, result.successful)
    }
  }

  @Test
  def testPeriod() :Unit ={
    {
      val result = parseAll(periodTerm, "3M")
      assertTrue(result.toString, result.successful)
      val expectedPeriod = Period.ofMonths(3)
      assertEquals(expectedPeriod, result.get)
    }
    {
      val result = parseAll(periodTerm, "7Y")
      assertTrue(result.toString, result.successful)
      val expectedPeriod = Period.ofYears(7)
      assertEquals(expectedPeriod, result.get)
    }
    {
      val result = parseAll(periodTerm, "1Y3M")
      assertTrue(result.toString, result.successful)
      val expectedPeriod = Period.ofYears(1).plusMonths(3)
      assertEquals(expectedPeriod, result.get)
    }
    {
      val result = parseAll(periodTerm, "9M2W-1D")
      assertTrue(result.toString, result.successful)
      val expectedPeriod = Period.ofMonths(9).plusDays(2*7).minusDays(1)
      assertEquals(expectedPeriod, result.get)
    }
  }

  @Test
  def testWholeNumber() :Unit ={
    {
      val result = parseAll(wholeNumber, "0099")
      assertTrue(result.toString, result.successful)
    }

    {
      val result = parseAll(wholeNumber, "3")
      assertTrue(result.toString, result.successful)
    }
  }

  @Test
  def testFra() :Unit ={
    val result = parseAll(fra, "3X6")
    assertTrue(result.toString, result.successful)
  }

  @Test
  def testTimeUnit() :Unit ={
    val result = parseAll(timeUnitTerm, "D")
    assertTrue(result.toString, result.successful)
  }

  @Test
  def testDeliveryMonth() :Unit ={
    val result = parseAll(deliveryMonth, "Z")
    assertTrue(result.toString, result.successful)
  }

  @Test
  def testDayOfMonth() :Unit = {
    val result = parseAll(dayOfMonth, "5")
    assertTrue(result.toString, result.successful)
  }

  @Test
  def testTerm() :Unit = {
    {
      val result = parseAll(term, "6W")
      assertTrue(result.toString, result.successful)
      result.get
    }
    {
      val result = parseAll(term, "2Y3M")
      assertTrue(result.toString, result.successful)
    }
  }

  @Test
  def testYear() : Unit = {
    {
      val result = parseAll(year4, "2015")
      assertTrue(result.toString, result.successful)
      val year2015 = result.get
      assertEquals(2015, year2015.getValue)
    }
  }

  @Test
  def testMonth() :Unit = {
    val result = parseAll(month, "May")
    assertTrue(result.toString, result.successful)
  }

  @Test
  def testImmTerm() :Unit = {
    {
      val result = parseAll(immTerm, "U6")
      assertTrue(result.toString, result.successful)
      val immDate = LocalDate.of(2016,9,21)
      assertEquals(immDate, result.get(Year.of(2015))) //LocalDate.of(2015,8,20)))
    }
  }
}