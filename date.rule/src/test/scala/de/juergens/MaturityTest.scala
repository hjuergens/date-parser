package de.juergens

import junit.framework.TestCase
import org.scalatest.junit.AssertionsForJUnit
import de.juergens.time.{EnrichedDate=>Date}
//import de.juergens.time.ExtendedDate

import org.junit.Ignore

@Ignore
class Maturity_Test extends TestCase with AssertionsForJUnit {

   def doMaturityTest(start: Date, rule: String, result: Date) = {
      expect(result, rule) {
         //Maturity(rule).date(start, Calendar.calendarForward)
      }
   }

   def testYearMonthDay() {

      doMaturityTest(Date(2011, 4, 13), "year + 1 > month 3 > day 14", Date(2012, 3, 14))
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

   def testSpecialMonthRules() {

      doMaturityTest(Date(2011, 4, 13), "firstOfMonth", Date(2011, 4, 1))
      doMaturityTest(Date(2011, 4, 13), "lastOfMonth", Date(2011, 5, 2)) // moved to business day with forward calendar
      doMaturityTest(Date(2011, 5, 13), "lastOfMonth", Date(2011, 5, 31))

      // last business day of month - using calendar with business rule ForwardUltimo
      expect(Date(2011, 4, 29), "lastOfMonth") {
         //new MaturityAlternative("lastOfMonth").date(Date(2011, 4, 13), Calendar(BusinessRule.ForwardUltimo))
      }
      expect(Date(2011, 5, 31), "lastOfMonth") {
         ///new MaturityAlternative("lastOfMonth").date(Date(2011, 5, 13), Calendar(BusinessRule.ForwardUltimo))
      }
   }

   def testFebruary() {

      doMaturityTest(Date(2011, 2, 28), "year -3", Date(2008, 2, 29))
      doMaturityTest(Date(2008, 2, 29), "year +3", Date(2011, 2, 28))
   }

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

   def testWeek() : Unit = {

      doMaturityTest(Date(2011, 4, 13), "week +1", Date(2011, 4, 20))
      doMaturityTest(Date(2011, 4, 13), "week -2", Date(2011, 3, 30))

      intercept[Exception] {
         doMaturityTest(Date(2011, 4, 13), "week 1", Date(2011, 4, 20))
      }
   }

   def testBusiness() {

      doMaturityTest(Date(2011, 4, 13), "business", Date(2011, 4, 13))
      doMaturityTest(Date(2011, 4, 13), "business +1", Date(2011, 4, 14))
      doMaturityTest(Date(2011, 4, 13), "business -1", Date(2011, 4, 12))
      doMaturityTest(Date(2011, 4, 13), "business +3", Date(2011, 4, 18)) // skips weekend
      doMaturityTest(Date(2011, 4, 13), "business 1", Date(2011, 4, 1)) // first business day of month
      doMaturityTest(Date(2011, 4, 13), "business 3", Date(2011, 4, 5)) // third business day of month
   }

   def testImm() {

      doMaturityTest(Date(2011, 4, 13), "imm", Date(2011, 6, 15))
      doMaturityTest(Date(2011, 6, 15), "imm", Date(2011, 6, 15))

      doMaturityTest(Date(2011, 4, 13), "imm +1", Date(2011, 9, 21))
      doMaturityTest(Date(2011, 4, 13), "imm +2", Date(2011, 12, 21))
      doMaturityTest(Date(2011, 4, 13), "imm -1", Date(2011, 3, 16))
   }

   def printSimulation(aStart: Date, aRule: String) {

      println("Simulating one year with: \"" + aRule + "\"")

      //val simValues = Maturity.simulateOneYear(
       //  aStart,
        // new MaturityAlternative(aRule),
         //Calendar.calendarForward)

      //simValues.map(pair => println(pair.toString() + " -> " + pair._2.toString()))
      println("-------------------------------")
   }

   // not a real test, just output
   def testSim() {

      // next third monday
      printSimulation(Date(2011, 4, 13), "monday 3 | month+1 > monday 3")

      // special rule for imm
      printSimulation(Date(2011, 4, 13), "imm")

      // the same without using the "imm" rule
      printSimulation(Date(2011, 4, 13), "march > wednesday 3 | june > wednesday 3 | september > wednesday 3 | december > wednesday 3 ")
   }

   // output only
   def testPsDateGrid() {
// TODO
//      val today = Date(2011, 4, 13)
//      val dateGrid =
//         for ((key, value) <- PsMaturity.psMaturityMap)
//         yield (key, new MaturityAlternative(value).date(today, Calendar.calendarForward))
//
//      // sort by date
//      val sorted = dateGrid.toList.sortWith((p1, p2) => p1._2 < p2._2)
//
//      for ((key, value) <- sorted)
//         println(key + "\t-> " + value)
   }
}

