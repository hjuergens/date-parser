/*
 * Copyright 2015 Hartmut Jürgens
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.juergens.text.parsers

import java.time.temporal._
import java.time.{DayOfWeek, LocalDate, YearMonth}

import de.juergens.text.DateRuleParsers
import de.juergens.util.Ordinal
import org.scalatest.Assertions._
import org.testng.Assert._
import org.testng.Reporter
import org.testng.annotations._

@Test(timeOut=1000)
class DateRuleParsersTestNG extends DateRuleParsers {

  def Date(y:Int,m:Int,d:Int) = LocalDate.of(y,m,d)

  @Test
  def weekDayTest() : Unit = {
    val isMonday  = parseAll(attribute, "monday").get
    assertEquals(isMonday.toString(), "Predicate(temporalAccessor=MONDAY)")
    assertTrue( isMonday.test(Date(2015,5,18)) )

    val isFriday  = parseAll(attribute, "friday").get
    assertEquals(isFriday.toString(), "Predicate(temporalAccessor=FRIDAY)")
    assertTrue( isFriday.test(Date(2015,5,22)) )
  }

  @Test
  def ordinalTest() : Unit = {
    assertEquals( parseAll(ordinal, "first").get, Ordinal(1) )
    assertEquals( parseAll(ordinal, "second").get, Ordinal(2) )
  }

  @Test
  def ordinalTimeUnitTest() : Unit = {
    val secondDay = parseAll(ordinalUnit, "second day")
    assertTrue(secondDay.successful, secondDay.toString)
    assertEquals(secondDay.get.toString, "2nd Day")
    assertEquals(secondDay.get(LocalDate.of(2015,8,16)), LocalDate.of(2015,8,18))
  }

  case class IsDayOfWeek(dayOfWeek:DayOfWeek) extends TemporalQuery[Boolean] {
    def isDayOfWeek(t: TemporalAccessor) = {
      t.ensuring(_.isSupported(ChronoField.DAY_OF_WEEK))
      DayOfWeek.from(t) equals dayOfWeek
    }
    override def queryFrom(temporal: TemporalAccessor) = isDayOfWeek(temporal)
    def apply(temporal: TemporalAccessor) : Boolean = isDayOfWeek(temporal)
  }

  @Test
  def nextWeek() : Unit = {
    val nextWeek = parseAll(adjuster, "next week").get

    val isSunday = IsDayOfWeek(DayOfWeek.SUNDAY)

    {
      val nextWeekDay = nextWeek(Date(2015, 5, 27))
      assertTrue(isSunday(nextWeekDay), DayOfWeek.from(nextWeekDay).toString)
      assertEquals(nextWeekDay, Date(2015, 5, 31))
    }

    {
      val nextWeekDay = nextWeek(Date(2015,5,31))
      assertTrue(isSunday(nextWeekDay), DayOfWeek.from(nextWeekDay).toString)
      assertEquals(nextWeekDay, Date(2015,6,7))
    }

    {
      val nextWeekDay = nextWeek(Date(2015,6,1))
      assertTrue(isSunday(nextWeekDay), DayOfWeek.from(nextWeekDay).toString)
      assertEquals(nextWeekDay, Date(2015,6,7))
    }
  }

  @Test
  def previousWeek() : Unit = {
    val previousWeek = parseAll(adjuster, "previous week").get

    val isSaturday = IsDayOfWeek(DayOfWeek.SATURDAY)

    {
      val previous = previousWeek(Date(2015,5,29))
      assertEquals( previous, Date(2015,5,23), previous.toString )
      assertTrue( previous.query(isSaturday), DayOfWeek.from(previous).toString )
    }

    {
      val previous = previousWeek(Date(2015,5,30))
      assertTrue( previous.query(isSaturday), DayOfWeek.from(previous).toString )
      assertEquals( previous, Date(2015,5,23) )
    }

    {
      val previous = previousWeek(Date(2015,5,31))
      assertTrue( previous.query(isSaturday), DayOfWeek.from(previous).toString)
      assertEquals( previous, Date(2015,5,30) )
    }

  }

  @Test
  def seekTest():Unit = {
    val anchor = Date(2015,5,23) // saturday

    val secondMondayAfter = parseAll(seekDayOfWeek, "second monday after").get
    assert( Date(2015,6,1) === secondMondayAfter(Date(2015,5,23)))
    assert( Date(2015,6,1) === secondMondayAfter(Date(2015,5,24)))
    assert( Date(2015,6,8) === secondMondayAfter(Date(2015,5,25)))
    assert( Date(2015,6,8) === secondMondayAfter(Date(2015,5,26)))
    Reporter.log(s"second monday after $anchor is ${secondMondayAfter(anchor)}")

    val fridayBefore = parseAll(seekDayOfWeek, "friday before").get
    assert( Date(2015,5,15) === fridayBefore(Date(2015,5,21)) )
    assert( Date(2015,5,15) === fridayBefore(Date(2015,5,22)), fridayBefore(Date(2015,5,22)) )
    assert( Date(2015,5,22) === fridayBefore(Date(2015,5,23)) )
    Reporter.log(s"friday before $anchor is ${fridayBefore(anchor)}")
  }

  @Test
  def seek2Test():Unit = {
    val threeMonthsAfter = parseAll(shifter, "three months after").get.apply _
    assert( Date(2015,8,22) === threeMonthsAfter(Date(2015,5,22)), threeMonthsAfter(Date(2015,5,22)) )
    assert( Date(2015,8,30) === threeMonthsAfter(Date(2015,5,30)) )
    assert( Date(2015,2,28) === threeMonthsAfter(Date(2014,11,30)) )
  }

  @Test
  def streamTest():Unit = {

    val everySecondDay = parseAll(stream, "every second day").get.apply _
    assert( Date(2015,5,22) === everySecondDay(Date(2015,5,22))(0) , everySecondDay(Date(2015,5,22))(0) )
    assert( Date(2015,5,24) === everySecondDay(Date(2015,5,22))(1) , everySecondDay(Date(2015,5,22))(1) )
    assert( Date(2015,6,1)  === everySecondDay(Date(2015,5,22))(5) , everySecondDay(Date(2015,5,22))(5) )
  }

  @Test
  def seekMonthTest():Unit = {

    // TODO 4. may versus fourth of may (4.may)
    val forthMayAfter = parseAll(seekMonth, "4. may after").get

    assertEquals(forthMayAfter(Date(2015,5,22)), Date(2019,5,31))
  }

  @Test
  def lastFirstSundayInAugustTest():Unit = {

    val lastSundayInAugust = parseAll(dayOfWeekInMonth, "last sunday in august")
    assertTrue(lastSundayInAugust.successful,lastSundayInAugust.toString)
    assertEquals(lastSundayInAugust.get(Date(2015,5,22)), Date(2015,8,30))
    assertEquals(lastSundayInAugust.get(Date(2015,9,17)), Date(2015,8,30))

    val firstSundayInAugust = parseAll(dayOfWeekInMonth, "first sunday in august")
    assertTrue(firstSundayInAugust.successful,firstSundayInAugust.toString)
    assertEquals(firstSundayInAugust.get(Date(2015,5,22)), Date(2015,8,2))
    assertEquals(firstSundayInAugust.get(Date(2015,9,17)), Date(2015,8,2))
  }

  @Test
  def testDayOfWeek() : Unit = {
    Reporter.log(parseAll(dayOfWeek, "monday").get.toString)

    Reporter.log(parseAll(ordinal, "second").get.toString)

    val isFriday = parseAll(attribute, "friday").get
    assertTrue(isFriday.test(Date(2015, 5, 22)))

    val isMonday = parseAll(attribute, "monday").get
    assert(isMonday.test(Date(2015, 5, 18)))

    parseAll(ordinalUnit, "second day")

    val anchor = Date(2015, 5, 23) // saturday

    val secondMondayAfter = parseAll(adjuster, "second monday after").get
    assert(Date(2015, 6, 1) === secondMondayAfter(Date(2015, 5, 23)))
    assert(Date(2015, 6, 1) === secondMondayAfter(Date(2015, 5, 24)))
    assert(Date(2015, 6, 8) === secondMondayAfter(Date(2015, 5, 25)))
    assert(Date(2015, 6, 8) === secondMondayAfter(Date(2015, 5, 26)))
    Reporter.log(s"second monday after $anchor is ${secondMondayAfter(anchor)}")

    val fridayBefore = parseAll(adjuster, "friday before").get
    assert(Date(2015, 5, 15) === fridayBefore(Date(2015, 5, 21)))
    assert(Date(2015, 5, 15) === fridayBefore(Date(2015, 5, 22)), fridayBefore(Date(2015, 5, 22)))
    assert(Date(2015, 5, 22) === fridayBefore(Date(2015, 5, 23)))
    Reporter.log(s"friday before $anchor is ${fridayBefore(anchor)}")

    val third = parseAll(ordinal, "3rd")
    val thirdWednesdayOf = parseAll(adjuster, "3rd Wednesday".toLowerCase).get
    assert(Date(2015, 9, 16) === thirdWednesdayOf(YearMonth.of(2015, 9)))
  }

  @Test
  def monthTest() : Unit = {
    val threeMonthsAfter = parseAll(shifter, "three months after").get.apply _
    assert(Date(2015, 8, 22) isEqual threeMonthsAfter(Date(2015, 5, 22)), threeMonthsAfter(Date(2015, 5, 22)))
    assert(Date(2015, 8, 30) isEqual threeMonthsAfter(Date(2015, 5, 30)))
    assert(Date(2015, 2, 28) isEqual threeMonthsAfter(Date(2014, 11, 30)))

    val everySecondDay = parseAll(stream, "every second day").get.apply _
    assert(Date(2015, 5, 22) isEqual everySecondDay(Date(2015, 5, 22))(0), everySecondDay(Date(2015, 5, 22))(0))
    assert(Date(2015, 5, 24) isEqual everySecondDay(Date(2015, 5, 22))(1), everySecondDay(Date(2015, 5, 22))(1))
    assert(Date(2015, 6, 1) isEqual everySecondDay(Date(2015, 5, 22))(5), everySecondDay(Date(2015, 5, 22))(5))
  }

  @Test
  def thirdWednesday() : Unit = {
    val anchor = Date(2014, 11, 30)

    {
      val thirdWednesday = parseAll(seekDayOfWeek, "third wednesday")
      val thirdWednesdayLocalDate = (t: LocalDate) => LocalDate.from(thirdWednesday.get(t))
      assertTrue(thirdWednesday.successful, thirdWednesday.toString)
      val result = thirdWednesdayLocalDate(anchor)
      assertEquals(result.getDayOfWeek, DayOfWeek.WEDNESDAY)
      assertTrue(anchor.until(result).getDays > 14, anchor.until(result).toString)
      assertEquals(result, Date(2014, 12, 17), s"third wednesday <> $result")
    }
  }

  @Test(enabled = false)
  def thirdWednesdayInNextQuarter() : Unit = {
    val nextQuarter = parseAll(adjuster, "next quarter")

    val anchor = Date(2014, 11, 30)
    val thirdWednesdayInNextQuarter = parseAll(adjuster, "third wednesday in next quarter")
    assertTrue(thirdWednesdayInNextQuarter.successful, thirdWednesdayInNextQuarter.toString)
    val dateAdjuster = (t: LocalDate) => LocalDate.from(thirdWednesdayInNextQuarter.get(t))
    val result = dateAdjuster(anchor)
    assertEquals(result.getDayOfWeek, DayOfWeek.WEDNESDAY)
    assertTrue(anchor.until(result).getDays > 14, anchor.until(result).toString)
    assertEquals(result, Date(2014, 12, 17), s"third wednesday <> $result")
  }

  @Test(enabled = false)
  def thirdWednesdayInEveryQuarter() : Unit = {
    val thirdWednesdayInEveryQuarter = parseAll(adjuster, "third wednesday in every quarter")
    assertTrue(thirdWednesdayInEveryQuarter.successful, thirdWednesdayInEveryQuarter.toString)
  }

  @Test(enabled = false)
  def twoDaysLater() : Unit = {
    import java.time.{LocalDate => Date}

    import xuwei_k.Scala2Java8.unaryOperator

    import scala.language.implicitConversions

    val twoDaysLater = parseAll(adjuster, "two days later")

    TemporalAdjusters.ofDateAdjuster((date:Date) => date.plusDays(2))
  }

  @Test(enabled = false)
  def firstDayOfNextMonthTest() : Unit = {
    val anchor = Date(2014, 11, 30)

    val ofNextMonth = parseAll(selector, "of next month")
    assertTrue(ofNextMonth.successful, ofNextMonth.toString)
    val firstDayofNextMonth1 = ofNextMonth.get(TemporalAdjusters.firstDayOfMonth())
    assertEquals(firstDayofNextMonth1(anchor), Date(2014,12,1))

    val firstDay = parseAll(ordinalUnit, "first day")
    assertTrue(firstDay.successful, firstDay.toString)
    //val k :  (Temporal) => LocalDate  = firstDay.get.apply
    val nextMonth = (temporal:Temporal) => temporal.plus(1, ChronoUnit.MONTHS)
    val firstDayofNextMonth2 = nextMonth.andThen[LocalDate](firstDay.get.apply)
    assertEquals(firstDayofNextMonth2(anchor), Date(2014,12,1))


    val firstDayOfNextMonth = parseAll(adjuster, "first day of next month")
    assertTrue(firstDayOfNextMonth.successful, firstDayOfNextMonth.toString)

    val dateAdjuster = (t: LocalDate) => LocalDate.from(firstDayOfNextMonth.get(t))
    val result = dateAdjuster(anchor)
    assertEquals(result.getDayOfMonth, 1)
    assertEquals(result.getMonthValue, 12)
  }
}