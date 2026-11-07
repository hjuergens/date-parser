/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.juergens.util


import java.time.temporal.ChronoUnit
import net.time4j.PlainDate
import org.junit.Assert._
import org.junit.Assume.assumeThat
import org.junit._
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assume.assumeThat
import org.hamcrest.CoreMatchers._
import org.junit.Assert.assertThat
import org.junit.Assume.assumeThat

@RunWith(classOf[JUnit4])
class IntervalTest {

  @Test
  def lowerBorder() {
    val lb = LowerBorder[Double](7.0) // [7,...)
    assertTrue(lb.apply(8))
    assertTrue(lb.apply(7))
    assertFalse(lb.apply(5))
  }

  @Test
  def upperBorder() {
    val lb = UpperBorder[Double](7.0) // (...,7]
    assertTrue(lb.apply(5))
    assertTrue(lb.apply(7))
    assertFalse(lb.apply(8))
  }

  @Test
  def lower_intersection() {
    val lb1 = LowerBorder[Double](7.0) // [7,...)
    val lb2 = LowerBorder[Double](13.0) // [13,...)
    val intersection = lb1 * lb2
    assertTrue(intersection.apply(15))
    assertTrue(intersection.apply(13))
    assertFalse(intersection.apply(7))
  }

  @Test
  def upper_union() {
    val ub1 = UpperBorder[Double](7.0) // (...,7]
    val ub2 = UpperBorder[Double](13.0) // (...,13]
    val union = ub1 + ub2
    assertTrue(union.apply(8))
    assertTrue(union.apply(13))
    assertFalse(union.apply(15))
  }

  @Test
  def intersection() {
    {
      val lb1 = LowerBorder[Int](4) // [4,...)
    val ub2 = UpperBorder[Int](22) // (...,22]
    val interval = lb1 * ub2
      assertFalse(interval.contains(3))
      assertTrue(interval.contains(4))
      assertTrue(interval.contains(10))
      assertTrue(interval.contains(22))
      assertFalse(interval.contains(300))
    }
  }

  @Test
  def interval_signum() {
    {
      val ub1 = UpperBorder[Double](7.0) // (...,7]
    val ub2 = UpperBorder[Double](13.0) // (...,13]

      {
        val interval = ub2 - ub1
        assertEquals("signum=" + interval.signum, 6.0, interval.signum, 1E-15)
      }
      {
        val interval = Interval(ub1, ub2)
        assertEquals("signum=" + interval.signum, 6.0, interval.signum, 1E-15)
      }
      {
        val interval = Interval(ub2, ub1)
        assertEquals("signum=" + interval.signum, -6.0, interval.signum, 1E-15)
      }
    }
    {
      val lb1 = LowerBorder[Float](152.25456f) // [152.25456, ...)
      val lb2 = LowerBorder[Float](6955.2863f) // [6955.2863, ...)

      {
        val interval = lb1 - lb2
        assertEquals("signum=" + interval.signum, 6803.0317f, interval.signum, 1E-15)
      }
      {
        val interval = Interval(lb1, lb2)
        assertEquals(s"signum=${interval.signum}", 6803.03173828125f , interval.signum, 1E-15)
      }
      {
        val interval = Interval(lb2, lb1)
        assertEquals(s"signum=${interval.signum}",-6803.0317f, interval.signum, 1E-15)
      }
    }
  }

  @Test
  def interval_contains() {
    {
      val ub1 = UpperBorder[Double](7.0) // (...,7]
    val ub2 = UpperBorder[Double](13.0) // (...,13]
    val interval = ub2 - ub1 // Interval(ub1, ub2)
      assertFalse(interval.contains(300))
      assertTrue(interval.contains(13))
      assertTrue(interval.contains(10))
      assertFalse(interval.contains(7))
      assertFalse(interval.contains(-3))
    }
    {
      val ub1 = UpperBorder[Double](7.0) // (...,7]
    val ub2 = UpperBorder[Double](13.0) // (...,13]
    val interval = ub1 - ub2 //Interval(ub2, ub1)
      assertFalse(interval.contains(300))
      assertFalse(interval.contains(13))
      assertFalse(interval.contains(10))
      assertFalse(interval.contains(7))
      assertFalse(interval.contains(-3))
      //         assertTrue(interval.empty[Double] == interval)
    }
    {
      val lb1 = LowerBorder[Int](4) // [4,...)
    val lb2 = LowerBorder[Int](22) // [22,...)
    val interval = lb1 - lb2 //Interval(ub2, ub1)
      assertFalse(interval.contains(-3))
      assertTrue(interval.contains(4))
      assertTrue(interval.contains(10))
      assertFalse(interval.contains(22))
      assertFalse(interval.contains(300))
    }
    {
      val lb1 = LowerBorder[Int](4) // [4,...)
    val lb2 = LowerBorder[Int](22) // [22,...)
    val interval = lb2 - lb1 //Interval(ub1, ub2)
      assertFalse(interval.contains(-3))
      assertFalse(interval.contains(4))
      assertFalse(interval.contains(10))
      assertFalse(interval.contains(22))
      assertFalse(interval.contains(300))
    }
  }

  @Test
  def interval_toString() {
    {
      val ub1 = UpperBorder[Double](7.0) // (...,7]
    val ub2 = UpperBorder[Double](13.0) // (...,13]
      assertEquals("7.0]" ,ub1.toString)
      assertEquals("(7.0" , (-ub1).toString)
      assertEquals("13.0]" , ub2.toString)
      assertEquals("(13.0" , (-ub2).toString)
      val interval = ub2 - ub1
      assertEquals("(7.0,13.0]", interval.toString)
    }
    {
      val ub1 = UpperBorder[Double](7.0) // (...,7]
    val ub2 = UpperBorder[Double](13.0) // (...,13]
    val interval = ub1 - ub2
      assertEquals("Ø", interval.toString, interval.toString)
    }
    {
      val lb1 = LowerBorder[Int](4) // [4,...)
    val lb2 = LowerBorder[Int](22) // [22,...)
      assertEquals("[4" , lb1.toString, lb1.toString())
      assertEquals("4)" , (-lb1).toString, (-lb1).toString())
      assertEquals("[22" , lb2.toString, lb2.toString())
      assertEquals("22)" , (-lb2).toString, (-lb2).toString())
      val interval = (lb1 - lb2)
      assertEquals("[4,22)" , interval.toString, interval.toString)
    }
    {
      val lb1 = LowerBorder[Int](4) // [4,...)
    val lb2 = LowerBorder[Int](22) // [22,...)
    val interval = (lb2 - lb1)
      assertEquals("Ø" , interval.toString, interval.toString)
    }
  }

  @Test
  def IntervalAsIntersection() {
    val lb1 = LowerBorder[Int](4) // [4,...)
    val lb2 = LowerBorder[Int](22) // [22,...)
    val ub1 = UpperBorder[Int](7) // (...,7]
    val ub2 = UpperBorder[Int](13) // (...,13]

    {
      val interval = ub1 * lb1 // [4,7]
      assertEquals("[4,7]", interval.toString)
      assertFalse(interval.contains(3))
      assertTrue(interval.contains(4))
      assertTrue(interval.contains(7))
      assertFalse(interval.contains(8))
      assert(7 - 4 == interval.signum, "signum=" + interval.signum)
    }

    {
      val interval = lb1 * ub2 // [4,13]
      assertEquals("[4,13]", interval.toString)
      assertFalse(interval.contains(3))
      assertTrue(interval.contains(4))
      assertTrue(interval.contains(13))
      assertFalse(interval.contains(14))
      assert(13 - 4 == interval.signum, "signum=" + interval.signum)
    }
  }

  @Test
  def IntervalAsDifference() {

    val lb04 = LowerBorder[Int](4) // [4,...)
    val lb22 = LowerBorder[Int](22) // [22,...)
    val ub07 = UpperBorder[Int](7) // (...,7]
    val ub13 = UpperBorder[Int](13) // (...,13]

    {
      val interval = lb04 - lb22 // [4,22)
      assertEquals("[4,22)" , interval.toString)
      assertFalse(interval.contains(3))
      assertTrue(interval.contains(4))
      assertTrue(interval.contains(21))
      assertFalse(interval.contains(22))
      assertEquals("signum=" + interval.signum, 18, interval.signum)
    }

    {
      val interval = -ub07 - lb22  // (7,22)
      assertEquals(interval.toString, "(7,22)", interval.toString)
      assertFalse(interval.contains(7))
      assertTrue(interval.contains(8))
      assertTrue(interval.contains(21))
      assertFalse(interval.contains(22))
      assertEquals("signum=" + interval.signum, 15, interval.signum)
    }

    {
      val interval = ub13 - ub07 // (7,13]
      assertEquals("(7,13]", interval.toString)
      assertFalse(interval.contains(7))
      assertTrue(interval.contains(8))
      assertTrue(interval.contains(13))
      assertFalse(interval.contains(24))
      assertEquals("signum=" + interval.signum, 6, interval.signum)
    }

    {
      val interval = -ub13 - lb22  // (13,22)
      assertEquals(interval.toString, "(13,22)", interval.toString)
      assertFalse(interval.contains(13))
      assertTrue(interval.contains(14))
      assertTrue(interval.contains(21))
      assertFalse(interval.contains(22))
      assertEquals("signum=" + interval.signum, 9, interval.signum)
    }
  }

  @Test
  def interval_intersection() :Unit = {

    val interval_ag = Interval("a","g")
    val interval_dj = Interval("d","j")

    assertEquals(Interval("d","g"), interval_ag * interval_dj)
    assertEquals("[d,g)", (interval_ag * interval_dj).toString)
    assertEquals(Interval("d","g"), interval_dj * interval_ag)
    assertEquals("[d,g)", (interval_dj * interval_ag).toString)

    val interval_bf = Interval("b","f")
    val interval_wy = Interval("w","y")

    try {
      interval_bf * interval_wy
      fail(s"The intervals $interval_bf and $interval_wy are disjunct.")
    } catch {
      case exp : IllegalArgumentException => assertNotNull(exp)
    }
    try {
      interval_wy * interval_bf
      fail(s"The intervals $interval_wy and $interval_bf are disjunct.")
    } catch {
      case exp : IllegalArgumentException => assertNotNull(exp)
    }

    {
      val interval_uv = Interval("u", true, "v", true)
      val interval_vw = Interval("v", true, "w", true)

      assertTrue((interval_uv * interval_vw).contains("v"))
      assertEquals(Interval("v", true, "v", true), interval_uv * interval_vw)
      assertEquals("[v,v]", (interval_uv * interval_vw).toString)
    }
  }

  @Test
  def interval_union() :Unit = {

    val interval_ag = Interval("a", "g")
    val interval_dj = Interval("d", "j")

    assertEquals(Interval("a", "j"), interval_ag + interval_dj)
    assertEquals(Interval("a", "j"), interval_dj + interval_ag)
    assertTrue((interval_ag + interval_dj).contains("a"))
    assertFalse((interval_ag + interval_dj).contains("j"))

    val interval_bf = Interval("b", "f")
    val interval_wy = Interval("w", "y")

    try {
      interval_bf + interval_wy
      fail(s"The intervals $interval_bf and $interval_wy are disjunct.")
    } catch {
      case exp: IllegalArgumentException => assertNotNull(exp)
    }
    try {
      interval_wy + interval_bf
      fail(s"The intervals $interval_wy and $interval_bf are disjunct.")
    } catch {
      case exp: IllegalArgumentException => assertNotNull(exp)
    }

    {
      val interval_uv = Interval("u", true, "v", false)
      val interval_vw = Interval("v", true, "w", false)

      assertTrue((interval_uv + interval_vw).contains("v"))
      assertEquals(Interval("u", "w"), interval_uv + interval_vw)
      assertEquals("[u,w)", (interval_uv + interval_vw).toString)
    }

    {
      val interval_uv = Interval("u", false, "v", true)
      val interval_vw = Interval("v", false, "w", true)

      assertTrue((interval_vw + interval_uv).contains("v"))
      assertEquals(Interval("u",false, "w", true), interval_vw + interval_uv)
      assertEquals("(u,w]", (interval_vw + interval_uv).toString)
    }

    {
      val interval_uv = Interval("u", true, "v", false)
      val interval_vw = Interval("v", false, "w", true)

      try {
        interval_vw + interval_uv
        fail(s"The intervals $interval_uv and $interval_vw are disjunct.")
      } catch {
        case exp: IllegalArgumentException => assertNotNull(exp)
      }
    }

    {
      val interval_79 = Interval(7, includeLower = false, 9, includeUpper = false)
      val interval_78 = Interval(7, includeLower = true, 8, includeUpper = true)

      assertEquals(Interval(7,includeLower = true, 9, includeUpper = false), interval_79 + interval_78)
      assertEquals(Interval(7,true, 9, includeUpper = false), interval_78 + interval_79)
    }

    {
      val interval_79 = Interval(7, includeLower = false, 9, includeUpper = true)
      val interval_78 = Interval(7, false, 8, includeUpper = false)

      assertEquals(Interval(7,includeLower = false, 9, includeUpper = true), interval_79 + interval_78)
      assertEquals(Interval(7,includeLower = false, 9, includeUpper = true), interval_78 + interval_79)
    }
  }

  @Test
  def unapply():Unit = {
    val Interval(a,b) = Interval(1,2)

    assertEquals(LowerBorder(1,include = true), a)
    assertEquals(UpperBorder(2,include = false), b)
  }

  @Test
  def localDate():Unit = {
    import java.time.LocalDate
    val start = LocalDate.of(2015,9,19)
    val end = start.plus(3, ChronoUnit.MONTHS)

    implicit val ord = new scala.math.Ordering[LocalDate] {
      override def compare(x: LocalDate, y: LocalDate): Int = x.compareTo(y)
    }
    val interval = Interval(start,end)

    assertTrue(interval.contains(start))
    assertTrue(interval.contains(start.plusDays(1)))
    assertTrue(interval.contains(end.minusDays(1)))
    assertFalse(interval.contains(end))
  }

  @Test
  def time4jDateInterval():Unit = {
    import java.time.LocalDate

    import net.time4j.range.DateInterval
    val start = LocalDate.of(2015,9,19)
    val end = start.plus(3, ChronoUnit.MONTHS)

    import scala.languageFeature.implicitConversions
    implicit def local2plain(ld:LocalDate) = PlainDate.from(ld)

    val interval = DateInterval.between(start,end)

    assertTrue(interval.contains(start))
    assertTrue(interval.contains(start.plusDays(1)))
    assertTrue(interval.contains(end.minusDays(1)))
    assumeThat(interval.contains(end), is(true))
  }
}
