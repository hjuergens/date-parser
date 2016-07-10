package de.juergens.text.parsers

import java.time.LocalDate
import java.time.temporal._

import de.juergens.text.TermParsers
import de.juergens.text.quantlib.QuantLibConverters._
import de.juergens.text.quantlib.{IsBusinessDay, QLDate}
import org.jquantlib.time.calendars.{Target => QLTarget}
import org.jquantlib.time.{BusinessDayConvention, Calendar => QLCalendar, Date => QL_Date, Period => QLPeriod}
import org.junit.Assert._
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import scala.language.implicitConversions

@RunWith(value = classOf[JUnit4])
class FinanceTermParsersTest extends TermParsers {

  @Test
  def testIsBusinessDay() :Unit = {
    {
      val isBusinessDay = new IsBusinessDay(new QLTarget)

      val targetAdjuster = new TemporalAdjuster {
        val calendar = new QLTarget
        val bdc = BusinessDayConvention.Following
        override def adjustInto(temporal: Temporal): Temporal = {
          calendar.adjust(QLDate.queryFrom(temporal), bdc)
        }
      }

      assertTrue(LocalDate.of(2015,8,21).query(isBusinessDay))
      assertFalse(LocalDate.of(2015,8,22).query(isBusinessDay))
      assertFalse(LocalDate.of(2015,8,23).query(isBusinessDay))
      assertTrue(LocalDate.of(2015,8,24).query(isBusinessDay))
      assertTrue(LocalDate.of(2015,8,25).query(isBusinessDay))
      assertTrue(LocalDate.of(2015,8,26).query(isBusinessDay))
      assertTrue(LocalDate.of(2015,8,27).query(isBusinessDay))
      assertTrue(LocalDate.of(2015,8,28).query(isBusinessDay))
      assertFalse(LocalDate.of(2015,8,29).query(isBusinessDay))
    }
  }

  @Test
  def testAdjust() :Unit = {
    {
      val businessDayAdjustment = new TemporalAdjuster {
        val calendar = new QLTarget
        val bdc = BusinessDayConvention.Following
        override def adjustInto(temporal: Temporal): Temporal = {
          calendar.adjust(QLDate.queryFrom(temporal), bdc)
        }
      }

      assertEquals(LocalDate.of(2016,4,29).`with`(businessDayAdjustment), LocalDate.of(2016,4,29))
      assertEquals(LocalDate.of(2016,4,30).`with`(businessDayAdjustment), LocalDate.of(2016,5,2))
    }
    {
      val businessDayAdjustment = new TemporalAdjuster {
        val calendar = new QLTarget
        val bdc = BusinessDayConvention.ModifiedFollowing
        override def adjustInto(temporal: Temporal): Temporal = {
          calendar.adjust(QLDate.queryFrom(temporal), bdc)
        }
      }

      assertEquals(LocalDate.of(2016,4,29).`with`(businessDayAdjustment), LocalDate.of(2016,4,29))
      assertEquals(LocalDate.of(2016,4,30).`with`(businessDayAdjustment), LocalDate.of(2016,4,29))
    }
  }

  @Test
  def testStandardPeriod() :Unit = {
    {
      val term3M = parseAll(standardPeriod, "3M")
      assertTrue(term3M.toString, term3M.successful)

      val referenceDate = LocalDate.of(2015,8,20)


      val mnth3 = term3M.get.addTo(referenceDate)

      assertEquals(mnth3, LocalDate.of(2015,11,20))
    }
  }

}