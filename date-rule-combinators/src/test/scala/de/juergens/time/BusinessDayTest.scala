package de.juergens.time

import java.time.LocalDate
import java.time.temporal.Temporal

import org.junit.Assert._
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
/**
 * Created by juergens on 14.08.15.
 */
@RunWith(classOf[JUnit4])
class BusinessDayTest {

  @Test(timeout = 2000)
  def unit() : Unit = {
    val holidays : Set[Temporal] = Set(LocalDate.of(2015,8,15),LocalDate.of(2015,8,16))

    {
      val nextBusiness = BusinessDay.unit(holidays).addTo(LocalDate.of(2015, 8, 14), 1)
      assertEquals(LocalDate.of(2015, 8, 17), nextBusiness)
    }

    {
      val nextBusiness = BusinessDay.unit(holidays).addTo(LocalDate.of(2015, 8, 13), 2)
      assertEquals(LocalDate.of(2015, 8, 17), nextBusiness)
    }

    {
      val nextBusiness = BusinessDay.unit(holidays).addTo(LocalDate.of(2015, 8, 15), 3)
      assertEquals(LocalDate.of(2015, 8, 19), nextBusiness)
    }
  }

  @Test(timeout = 2000)
  def between() : Unit = {
    val holidays : Set[Temporal] = Set(LocalDate.of(2015,8,15),LocalDate.of(2015,8,16))

    {
      val businessDays = BusinessDay.unit(holidays).between(LocalDate.of(2015, 8, 14), LocalDate.of(2015, 8, 17))
      assertEquals(1, businessDays)
    }

    {
      val businessDays = BusinessDay.unit(holidays).between(LocalDate.of(2015, 8, 13), LocalDate.of(2015, 8, 17))
      assertEquals(2, businessDays)
    }

    {
      val businessDays = BusinessDay.unit(holidays).between(LocalDate.of(2015, 8, 13), LocalDate.of(2015, 8, 18))
      assertEquals(3, businessDays)
    }

  }

}
