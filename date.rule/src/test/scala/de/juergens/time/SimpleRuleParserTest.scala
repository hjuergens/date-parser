/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.juergens.time

import java.time.LocalDate

import de.juergens.text.SimpleRuleParser
import org.junit._
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.scalatest.Assertions._

import scala.util.parsing.input.CharSequenceReader

@deprecated("concerning old parser", "0.0.3")
@RunWith(classOf[JUnit4])
class SimpleRuleParserTest {

  def Date(year: Int, month: Int, dayOfMonth: Int): LocalDate = LocalDate.of(year, month, dayOfMonth)

  @Test
  def factor() {
    val parser = new SimpleRuleParser

    {
      parser.month(new CharSequenceReader("march"))
      val result00 = parser.parse(parser.month, "march").get.toString
      assert("march" == result00.toLowerCase, result00)

      val result01 = parser.parse(parser.fixRule, "wednesday 3").get.toString
      assert("3 wednesday" == result01.toLowerCase, result01)

      val result02 = parser.parse(parser.factor, "(march 0 > wednesday 3)").get.toString
      assert("(0 march>3 wednesday)" == result02.toLowerCase, result02)
    }

    {
      val result = parser.parse(parser.factor, "(june > wednesday 3)").get.toString
      assert("(0 june>3 wednesday)" == result.toLowerCase, result)
    }

    {
      val result = parser.parse(parser.factor, "(september > wednesday 3)").get.toString
      assert("(0 september>3 wednesday)" == result.toLowerCase, result)
    }

    {
      val result = parser.parse(parser.factor, "(december > wednesday 3)").get.toString
      assert("(0 december>3 wednesday)" == result.toLowerCase, result)
    }
  }

  @Test
  @Ignore
  def expr() {
    val parser = new SimpleRuleParser

    {
      // "march > wednesday 3 | june > wednesday 3 | september > wednesday 3 | december > wednesday 3 "
      val result = parser.parse(parser.expr, "(march > wednesday 3) | (june > wednesday 3) | (september > wednesday 3) | (december > wednesday 3) ").get.toString
      assert("(0 Mar>3 wednesday)|(0 Jun>3 wednesday)|(0 Sep>3 wednesday)|(0 Dec>3 wednesday)" == result, result)
    }

    {
      val result = parser.parse(parser.factor, "(december > wednesday 3 > day +2)").get.toString
      assert("(0 Dec>3 wednesday>+2 day)" == result, result)
    }

    {
      val result = parser.parse(parser.factor, "(year + 1 > month 3 > day 14)").get.toString
      assert("(+1 year>3 month>14 day)" == result, result)
    }
  }

  @Test
  def timeUnit() {
    val parser = new SimpleRuleParser

    {
      val result = parser.parse(parser.timeUnit, "year").get.toString
      assert("Years" == result, result)
    }

    {
      val result = parser.parse(parser.timeUnit, "day").get.toString
      assert("Days" == result, result)
    }
  }

  @Test
  def weekDay() {
    val parser = new SimpleRuleParser

    {
      val result = parser.parse(parser.weekDay, "wednesday").get.toString
      assert("wednesday" == result.toLowerCase, result)
    }
  }

  @Test
  @Ignore
  def fixRule() {
    val parser = new SimpleRuleParser

    {
      val result = parser.parseAll(parser.fixRule, "month 3").get.toString
      assert("3 month" == result, result)
    }

    {
      val result = parser.parseAll(parser.fixRule, "day 14").get.toString
      assert("14 day" == result, result)
      println(parser.toString)
    }
  }

  @Test
  def shift() {
    val parser = new SimpleRuleParser
    assert("(+,1)" == parser.parse(parser.shift, "+1").get.toString)
  }

  @Test
  @Ignore
  def shiftRule() {
    val parser = new SimpleRuleParser

    {
      val result = parser.parse(parser.shiftRule, "year +1").get.toString
      assert("+1 year" == result, result)
    }

    {
      // "wednesday -1"
      val result = parser.parse(parser.shiftRule, "wednesday -1").get.toString
      assert("-1 wednesday" == result, result)
    }

    {
      // "wednesday - 0"
      val result = parser.parse(parser.shiftRule, "wednesday - 0").get.toString
      assert("+0 wednesday" == result, result)
    }

    {
      // "quarter +1"
      val result = parser.parse(parser.shiftRule, "quarter +1").get.toString
      assert("+1 quarter" == result, result)
    }

    {
      // "week -2"
      val result = parser.parse(parser.shiftRule, "week -2").get.toString
      assert("-2 week" == result, result)
    }

    {
      val rule = parser.parse(parser.shiftRule, "year +3").get
      //assert(Date(2011, 2, 28) == rule.evaluate(Date(2008, 2, 29)))
    }

    {
      val rule = parser.parse(parser.shiftRule, "wednesday -1").get
      //assert(Date(2011, 4, 6) == rule.evaluate(Date(2011, 4, 13)))
    }
    // "business 3"
    // "business +1"
  }

  @Ignore
  @Test
  def testYearMonthDay() {

    doTest(Date(2011, 4, 13), "(year + 1 > month 3 > day 14)", Date(2012, 3, 14))
    doTest(Date(2011, 4, 13), " Year -1 > Month +1", Date(2010, 5, 13))
    doTest(Date(2011, 4, 13), "year 2007 > day + 3", Date(2007, 4, 16))
    doTest(Date(2011, 4, 13), "year 2007 > day -1", Date(2007, 4, 12))

    // invalid values are currently asserted
    intercept[AssertionError] {
      doTest(Date(2011, 4, 13), "day 32", Date(2007, 4, 12))
    }
    intercept[AssertionError] {
      doTest(Date(2011, 4, 13), "month 13", Date(2007, 4, 12))
    }

    // unknown rule "Jahr" causes an exception
    intercept[Exception] {
      doTest(Date(2011, 4, 13), "Jahr 2010", Date(2007, 4, 12))
    }
  }

  @Ignore
  @Test
  def testSpecialMonthRules() {

    doTest(Date(2011, 4, 13), "firstOfMonth", Date(2011, 4, 1))
    doTest(Date(2011, 4, 13), "lastOfMonth", Date(2011, 5, 2)) // moved to business day with forward calendar
    doTest(Date(2011, 5, 13), "lastOfMonth", Date(2011, 5, 31))

    //      // last business day of month - using calendar with business rule ForwardUltimo
    //      expect(Date(2011, 4, 29), "lastOfMonth") {
    //         new MaturityAlternative("lastOfMonth").date(Date(2011, 4, 13), Calendar(BusinessRule.ForwardUltimo))
    //      }
    //      expect(Date(2011, 5, 31), "lastOfMonth") {
    //         new MaturityAlternative("lastOfMonth").date(Date(2011, 5, 13), Calendar(BusinessRule.ForwardUltimo))
    //      }
  }

  @Test(timeout = 1000)
  @Ignore
  def testFebruary() {
    //      rule.date(start, Calendar.calendarForward)

    doTest(Date(2011, 2, 28), "year -3", Date(2008, 2, 28))
    doTest(Date(2008, 2, 29), "year +3", Date(2011, 2, 28))
  }

  @Test
  @Ignore
  def testNamedMonth() {

    doTest(Date(2011, 4, 13), "april", Date(2011, 4, 13))
    doTest(Date(2011, 4, 13), "may", Date(2011, 5, 13))
    doTest(Date(2011, 4, 13), "march", Date(2012, 3, 13)) // next year

    doTest(Date(2011, 4, 13), "april+1", Date(2012, 4, 13))
    doTest(Date(2011, 4, 13), "may +1", Date(2012, 5, 14)) // moved to business
    doTest(Date(2011, 4, 13), "mar +1", Date(2013, 3, 13))
    doTest(Date(2011, 4, 13), "mar -1", Date(2011, 3, 14)) // moved to business

    // no absolute values
    intercept[Exception] {
      doTest(Date(2011, 4, 13), "mar 1", Date(2011, 3, 13))
    }
  }

  @Test
  @Ignore
  def testWeekDays() {

    doTest(Date(2011, 4, 13), "monday", Date(2011, 4, 18)) // next monday
    doTest(Date(2011, 4, 13), "monday 1", Date(2011, 4, 4)) // first monday in month
    doTest(Date(2011, 4, 13), "monday + 1", Date(2011, 4, 25))
    doTest(Date(2011, 4, 13), "monday -1", Date(2011, 4, 11))

    doTest(Date(2011, 4, 13), "friday 1", Date(2011, 4, 1)) // first friday in month
    doTest(Date(2011, 4, 13), "friday 2", Date(2011, 4, 8)) // second friday in month

    doTest(Date(2011, 4, 13), "wednesday", Date(2011, 4, 13))
    doTest(Date(2011, 4, 13), "wednesday + 0", Date(2011, 4, 13))
    doTest(Date(2011, 4, 13), "wednesday - 0", Date(2011, 4, 13))
    doTest(Date(2011, 4, 13), "wednesday + 1", Date(2011, 4, 20))
    doTest(Date(2011, 4, 13), "wednesday + 2", Date(2011, 4, 27))
    doTest(Date(2011, 4, 13), "wednesday -1", Date(2011, 4, 6))
  }

  @Test
  @Ignore
  def testQuarter() {

    doTest(Date(2011, 1, 4), "quarter +1", Date(2011, 4, 4))
    doTest(Date(2011, 1, 4), "quarter +2", Date(2011, 7, 4))
    doTest(Date(2011, 1, 4), "quarter -1", Date(2010, 10, 4))

    intercept[AssertionError] {
      doTest(Date(2011, 1, 4), "quarter", Date(2011, 1, 4))
    }

    // transpose to quarter in the given year (allowed values 1-4)
    doTest(Date(2011, 1, 4), "quarter 1", Date(2011, 1, 4))
    doTest(Date(2011, 1, 4), "quarter 2", Date(2011, 4, 4))
    doTest(Date(2011, 2, 12), "quarter 3", Date(2011, 8, 12))
    doTest(Date(2011, 5, 12), "quarter 3", Date(2011, 8, 12))
    doTest(Date(2011, 1, 4), "quarter 4", Date(2011, 10, 4))
  }

  @Test(timeout = 1000)
  @Ignore
  def testWeek() {

    doTest(Date(2011, 4, 13), "week +1", Date(2011, 4, 20))
    doTest(Date(2011, 4, 13), "week -2", Date(2011, 3, 30))

    intercept[Exception] {
      doTest(Date(2011, 4, 13), "week 1", Date(2011, 4, 20))
    }
  }

  @Test
  @Ignore
  def testBusiness() {

    doTest(Date(2011, 4, 13), "business", Date(2011, 4, 13))
    doTest(Date(2011, 4, 13), "business +1", Date(2011, 4, 14))
    doTest(Date(2011, 4, 13), "business -1", Date(2011, 4, 12))
    doTest(Date(2011, 4, 13), "business +3", Date(2011, 4, 18)) // skips weekend
    doTest(Date(2011, 4, 13), "business 1", Date(2011, 4, 1)) // first business day of month
    doTest(Date(2011, 4, 13), "business 3", Date(2011, 4, 5)) // third business day of month
  }

  @Test
  @Ignore
  def testImm() {

    doTest(Date(2011, 4, 13), "imm", Date(2011, 6, 15))
    doTest(Date(2011, 6, 15), "imm", Date(2011, 6, 15))

    doTest(Date(2011, 4, 13), "imm +1", Date(2011, 9, 21))
    doTest(Date(2011, 4, 13), "imm +2", Date(2011, 12, 21))
    doTest(Date(2011, 4, 13), "imm -1", Date(2011, 3, 16))
  }

  def doTest(start: LocalDate, ruleStr: String, expectedDate: LocalDate) = {
    expect(expectedDate, ruleStr) {
      val parser = new SimpleRuleParser
      val rule = parser.parse(parser.expr, ruleStr).get
      println("*=" + rule.evaluate(start)(expectedDate))
      rule.date(start, Calendar.nullCalendar)
    }
  }
}
