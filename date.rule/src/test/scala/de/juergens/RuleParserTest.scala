/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.juergens

import org.junit._
import org.scalatest.Assertions._

import de.juergens.time.{EnrichedDate=>Date}
import de.juergens.time.Calendar
import scala.util.parsing.input.CharSequenceReader

@Ignore
class RuleParserTest {

  @Before
  def setUp(): Unit = {
  }

  @After
  def tearDown(): Unit = {
  }

   @Test
   def factor() {
      val parser = new RuleParser

      {
         parser.month(new CharSequenceReader("march"))
         val result00 = parser.parse(parser.month, "march").get.toString
         assert("Mar" == result00, result00)

         val result01 = parser.parse(parser.fixRule, "wednesday 3").get.toString
         assert("3 wednesday" == result01, result01)

         val result02 = parser.parse(parser.factor, "(march 0 > wednesday 3)").get.toString
         assert("(0 Mar>3 wednesday)" == result02, result02)
      }

      {
         val result = parser.parse(parser.factor, "(june > wednesday 3)").get.toString
         assert("(0 Jun>3 wednesday)" == result, result)
      }

      {
         val result = parser.parse(parser.factor, "(september > wednesday 3)").get.toString
         assert("(0 Sep>3 wednesday)" == result, result)
      }

      {
         val result = parser.parse(parser.factor, "(december > wednesday 3)").get.toString
         assert("(0 Dec>3 wednesday)" == result, result)
      }
   }

   @Test
   def expr() {
      val parser = new RuleParser

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
      val parser = new RuleParser

      {
         val result = parser.parse(parser.timeUnit, "year").get.toString
         assert("year" == result, result)
      }

      {
         val result = parser.parse(parser.timeUnit, "day").get.toString
         assert("day" == result, result)
      }
   }

   @Test
   def weekDay() {
      val parser = new RuleParser

      {
         val result = parser.parse(parser.weekDay, "wednesday").get.toString
         assert("wednesday" == result, result)
      }
   }

   @Test
   def fixRule() {
      val parser = new RuleParser

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
      val parser = new RuleParser
      assert("(+,1)" == parser.parse(parser.shift, "+1").get.toString)
   }

   @Test
   def shiftRule() {
      val parser = new RuleParser

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

   def doMaturityTest(start: Date, ruleStr: String, expectedDate: Date) = {
      expect(expectedDate, ruleStr) {
         val parser = new RuleParser
         val rule = parser.parse(parser.expr, ruleStr).get
         println("*=" + rule.evaluate(start, expectedDate))
         rule.date(start, Calendar.calendarForward)
      }
   }

   @Ignore
   @Test
   def testYearMonthDay() {

      doMaturityTest(Date(2011, 4, 13), "(year + 1 > month 3 > day 14)", Date(2012, 3, 14))
      doMaturityTest(Date(2011, 4, 13), " Year -1 > Month +1", Date(2010, 5, 13))
      doMaturityTest(Date(2011, 4, 13), "year 2007 > day + 3", Date(2007, 4, 16))
      doMaturityTest(Date(2011, 4, 13), "year 2007 > day -1", Date(2007, 4, 12))

      // invalid values are currently asserted
      intercept[AssertionError] {
         doMaturityTest(Date(2011, 4, 13), "day 32", Date(2007, 4, 12))
      }
      intercept[AssertionError] {
         doMaturityTest(Date(2011, 4, 13), "month 13", Date(2007, 4, 12))
      }

      // unknown rule "Jahr" causes an exception
      intercept[Exception] {
         doMaturityTest(Date(2011, 4, 13), "Jahr 2010", Date(2007, 4, 12))
      }
   }

   @Ignore
   @Test
   def testSpecialMonthRules() {

      doMaturityTest(Date(2011, 4, 13), "firstOfMonth", Date(2011, 4, 1))
      doMaturityTest(Date(2011, 4, 13), "lastOfMonth", Date(2011, 5, 2)) // moved to business day with forward calendar
      doMaturityTest(Date(2011, 5, 13), "lastOfMonth", Date(2011, 5, 31))

//      // last business day of month - using calendar with business rule ForwardUltimo
//      expect(Date(2011, 4, 29), "lastOfMonth") {
//         new MaturityAlternative("lastOfMonth").date(Date(2011, 4, 13), Calendar(BusinessRule.ForwardUltimo))
//      }
//      expect(Date(2011, 5, 31), "lastOfMonth") {
//         new MaturityAlternative("lastOfMonth").date(Date(2011, 5, 13), Calendar(BusinessRule.ForwardUltimo))
//      }
   }

   @Test
   def testFebruary() {
//      rule.date(start, Calendar.calendarForward)

      doMaturityTest(Date(2011, 2, 28), "year -3", Date(2008, 2, 28))
      doMaturityTest(Date(2008, 2, 29), "year +3", Date(2011, 2, 28))
   }

   @Test
   def testNamedMonth() {

      doMaturityTest(Date(2011, 4, 13), "april", Date(2011, 4, 13))
      doMaturityTest(Date(2011, 4, 13), "may", Date(2011, 5, 13))
      doMaturityTest(Date(2011, 4, 13), "march", Date(2012, 3, 13)) // next year

      doMaturityTest(Date(2011, 4, 13), "april+1", Date(2012, 4, 13))
      doMaturityTest(Date(2011, 4, 13), "may +1", Date(2012, 5, 14)) // moved to business
      doMaturityTest(Date(2011, 4, 13), "mar +1", Date(2013, 3, 13))
      doMaturityTest(Date(2011, 4, 13), "mar -1", Date(2011, 3, 14)) // moved to business

      // no absolute values
      intercept[Exception] {
         doMaturityTest(Date(2011, 4, 13), "mar 1", Date(2011, 3, 13))
      }
   }

   @Test
   def testWeekDays() {

      doMaturityTest(Date(2011, 4, 13), "monday", Date(2011, 4, 18)) // next monday
      doMaturityTest(Date(2011, 4, 13), "monday 1", Date(2011, 4, 4)) // first monday in month
      doMaturityTest(Date(2011, 4, 13), "monday + 1", Date(2011, 4, 25))
      doMaturityTest(Date(2011, 4, 13), "monday -1", Date(2011, 4, 11))

      doMaturityTest(Date(2011, 4, 13), "friday 1", Date(2011, 4, 1)) // first friday in month
      doMaturityTest(Date(2011, 4, 13), "friday 2", Date(2011, 4, 8)) // second friday in month

      doMaturityTest(Date(2011, 4, 13), "wednesday", Date(2011, 4, 13))
      doMaturityTest(Date(2011, 4, 13), "wednesday + 0", Date(2011, 4, 13))
      doMaturityTest(Date(2011, 4, 13), "wednesday - 0", Date(2011, 4, 13))
      doMaturityTest(Date(2011, 4, 13), "wednesday + 1", Date(2011, 4, 20))
      doMaturityTest(Date(2011, 4, 13), "wednesday + 2", Date(2011, 4, 27))
      doMaturityTest(Date(2011, 4, 13), "wednesday -1", Date(2011, 4, 6))
   }

   @Test
   def testQuarter() {

      doMaturityTest(Date(2011, 1, 4), "quarter +1", Date(2011, 4, 4))
      doMaturityTest(Date(2011, 1, 4), "quarter +2", Date(2011, 7, 4))
      doMaturityTest(Date(2011, 1, 4), "quarter -1", Date(2010, 10, 4))

      intercept[AssertionError] {
         doMaturityTest(Date(2011, 1, 4), "quarter", Date(2011, 1, 4))
      }

      // transpose to quarter in the given year (allowed values 1-4)
      doMaturityTest(Date(2011, 1, 4), "quarter 1", Date(2011, 1, 4))
      doMaturityTest(Date(2011, 1, 4), "quarter 2", Date(2011, 4, 4))
      doMaturityTest(Date(2011, 2, 12), "quarter 3", Date(2011, 8, 12))
      doMaturityTest(Date(2011, 5, 12), "quarter 3", Date(2011, 8, 12))
      doMaturityTest(Date(2011, 1, 4), "quarter 4", Date(2011, 10, 4))
   }

   @Test
   def testWeek() {

      doMaturityTest(Date(2011, 4, 13), "week +1", Date(2011, 4, 20))
      doMaturityTest(Date(2011, 4, 13), "week -2", Date(2011, 3, 30))

      intercept[Exception] {
         doMaturityTest(Date(2011, 4, 13), "week 1", Date(2011, 4, 20))
      }
   }

   @Test
   def testBusiness() {

      doMaturityTest(Date(2011, 4, 13), "business", Date(2011, 4, 13))
      doMaturityTest(Date(2011, 4, 13), "business +1", Date(2011, 4, 14))
      doMaturityTest(Date(2011, 4, 13), "business -1", Date(2011, 4, 12))
      doMaturityTest(Date(2011, 4, 13), "business +3", Date(2011, 4, 18)) // skips weekend
      doMaturityTest(Date(2011, 4, 13), "business 1", Date(2011, 4, 1)) // first business day of month
      doMaturityTest(Date(2011, 4, 13), "business 3", Date(2011, 4, 5)) // third business day of month
   }

   @Test
   def testImm() {

      doMaturityTest(Date(2011, 4, 13), "imm", Date(2011, 6, 15))
      doMaturityTest(Date(2011, 6, 15), "imm", Date(2011, 6, 15))

      doMaturityTest(Date(2011, 4, 13), "imm +1", Date(2011, 9, 21))
      doMaturityTest(Date(2011, 4, 13), "imm +2", Date(2011, 12, 21))
      doMaturityTest(Date(2011, 4, 13), "imm -1", Date(2011, 3, 16))
   }



}
