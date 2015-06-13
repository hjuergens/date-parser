package de.juergens.text

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
    assertEquals(isMonday.toString(), "is monday?")
    assertTrue( isMonday.test(Date(2015,5,18)) )

    val isFriday  = parseAll(attribute, "friday").get
    assertEquals(isFriday.toString(), "is friday?")
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
  def nextWeek():Unit = {
    // TimeUnitShifter
    val nextWeek = parseAll(unknown, "next week").get.shift _
    assertEquals( nextWeek(Date(2015,5,27)),Date(2015,5,31) )
    assertEquals( nextWeek(Date(2015,5,31)),Date(2015,6,7)  )
    assertEquals( nextWeek(Date(2015,6,1) ),Date(2015,6,7)  )
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
  @Ignore
  def test() : Unit = {
    val anchor = Date(2015,5,23) // saturday
//    assert(Saturday == WeekDayPredicate.weekDay()(anchor))



    //val nextThirdWednesdayInQarter = parseAll(?, "next third wednesday in quarter")

    //val thirdWednesdayInNextQuarter = parseAll(?, "third wednesday in next quarter")
  }
}