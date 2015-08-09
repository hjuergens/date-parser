package de.juergens.text

import java.time.{YearMonth, Year, LocalDate, DayOfWeek}
import java.time.temporal._

import de.juergens.time.Seek.SeekExtended
import de.juergens.time._
import de.juergens.time.impl.DayShifter
import de.juergens.util.Ordinal
import org.junit.Ignore
import org.testng.Assert._
import org.testng.annotations._




@Test(timeOut=1000)
class DateRuleParserTestNG extends DateRuleParser {

  @Test
  def weekDayTest() : Unit = {
    val isMonday  = parseAll(attribute, "monday").get
//    assertEquals(isMonday.toString(), "is monday?")
    assertTrue( isMonday.test(Date(2015,5,18)) )

    val isFriday  = parseAll(attribute, "friday").get
//    assertEquals(isFriday.toString(), "is friday?")
    assertTrue( isFriday.test(Date(2015,5,22)) )
  }

  @Test
  def ordinalTest() : Unit = {
    assertEquals( parseAll(ordinal, "second").get, Ordinal(2) )
  }

  @Test
  def ordinalTimeUnitTest() : Unit = {
    assertEquals( parseAll(ordinalTimeUnit, "second day").get, DayShifter(2) )
  }

  @Test
  def nextWeek() : Unit = {
    val nextWeek = parseAll(adjuster, "next week").get

    assertEquals( nextWeek(Date(2015,5,27)),Date(2015,5,31) )
    assertTrue( nextWeek(Date(2015,5,27)).get(ChronoField.DAY_OF_WEEK) == DayOfWeek.SUNDAY.getValue )

    assertEquals( nextWeek(Date(2015,5,31)),Date(2015,6,7)  )
    assertTrue( nextWeek(Date(2015,5,31)).get(ChronoField.DAY_OF_WEEK) == DayOfWeek.SUNDAY.getValue )

    assertEquals( nextWeek(Date(2015,6,1) ),Date(2015,6,7)  )
    assertTrue( nextWeek(Date(2015,6,1)).get(ChronoField.DAY_OF_WEEK) == DayOfWeek.SUNDAY.getValue )
  }

  @Test
  def previousWeek() : Unit = {
    val previousWeek = parseAll(adjuster, "previous week").get

    object IsSaturday extends TemporalQuery[Boolean] {
      def isSaturday(t: TemporalAccessor) = DayOfWeek.from(t) == DayOfWeek.SATURDAY
      override def queryFrom(temporal: TemporalAccessor) = isSaturday(temporal)
    }

    {
      val previous = previousWeek(Date(2015,5,29))
      assertEquals( previous, Date(2015,5,23) )
      assertTrue( previous.query(IsSaturday) )
    }

    {
      val previous = previousWeek(Date(2015,5,30))
      assertEquals( previous, Date(2015,5,23) )
      assertTrue( previous.query(IsSaturday) )
    }

    {
      val previous = previousWeek(Date(2015,5,31))
      assertEquals( previous, Date(2015,5,30) )
      assertTrue( previous.query(IsSaturday) )
    }

  }

  @Test
  def seekTest():Unit = {
    val anchor = Date(2015,5,23) // saturday

    val secondMondayAfter : Seek = parseAll(seekWeekDay, "second monday after").get
    assert( Date(2015,6,1) == secondMondayAfter(Date(2015,5,23)))
    assert( Date(2015,6,1) == secondMondayAfter(Date(2015,5,24)))
    assert( Date(2015,6,8) == secondMondayAfter(Date(2015,5,25)))
    assert( Date(2015,6,8) == secondMondayAfter(Date(2015,5,26)))
    println(s"second monday after ${anchor} is ${secondMondayAfter(anchor)}")

    val fridayBefore : Seek = parseAll(seekWeekDay, "friday before").get
    assert( Date(2015,5,15) == fridayBefore(Date(2015,5,21)) )
    assert( Date(2015,5,15) == fridayBefore(Date(2015,5,22)), fridayBefore(Date(2015,5,22)) )
    assert( Date(2015,5,22) == fridayBefore(Date(2015,5,23)) )
    println(s"friday before ${anchor} is ${fridayBefore(anchor)}")
  }

  @Test
  def seek2Test():Unit = {
    val threeMonthsAfter = parseAll(seek2, "three months after").get.shift _
    assert( Date(2015,8,22) == threeMonthsAfter(Date(2015,5,22)), threeMonthsAfter(Date(2015,5,22)) )
    assert( Date(2015,8,30) == threeMonthsAfter(Date(2015,5,30)) )
    assert( Date(2015,2,28) == threeMonthsAfter(Date(2014,11,30)) )
  }

  @Test
  def streamTest():Unit = {

    val everySecondDay = parseAll(stream, "every second day").get.apply _
    assert( Date(2015,5,22) == everySecondDay(Date(2015,5,22))(0) , everySecondDay(Date(2015,5,22))(0) )
    assert( Date(2015,5,24) == everySecondDay(Date(2015,5,22))(1) , everySecondDay(Date(2015,5,22))(1) )
    assert( Date(2015,6,1)  == everySecondDay(Date(2015,5,22))(5) , everySecondDay(Date(2015,5,22))(5) )
  }

  @Test
  def seekMonthTest():Unit = {

    // TODO 4. may versus fourth of may (4.may)
    val forthMayAfter = parseAll(seekMonth, "4. may after").get

    assertEquals(forthMayAfter(Date(2015,5,22)), Date(2019,5,31))
  }

  @Test
  def lastSundayInAugustTest():Unit = {

    val lastSundayInAugust = parseAll(adjuster_, "last sunday in august").get

    assertEquals(lastSundayInAugust(Date(2015,5,22)), Date(2015,8,30))
    assertEquals(lastSundayInAugust(Date(2015,9,17)), Date(2015,8,30))

    val firstSundayInAugust = parseAll(adjuster_, "first sunday in august").get
    assertEquals(firstSundayInAugust(Date(2015,5,22)), Date(2015,8,2))
    assertEquals(firstSundayInAugust(Date(2015,9,17)), Date(2015,8,2))
  }

}