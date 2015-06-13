/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.juergens.util

import org.junit._
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class IntervalTest {
   @Test
   def lowerBorder() {
      val lb = new LowerBorder[Double](7.0) // [7,...)
      assert(lb.apply(8))
      assert(lb.apply(7))
      assert(!lb.apply(5))
   }

   @Test
   def upperBorder() {
      val lb = new UpperBorder[Double](7.0) // (...,7]
      assert(lb.apply(5))
      assert(lb.apply(7))
      assert(!lb.apply(8))
   }

   @Test
   def lower_intersection() {
      val lb1 = new LowerBorder[Double](7.0) // [7,...)
      val lb2 = new LowerBorder[Double](13.0) // [13,...)
      val intersection = lb1 * lb2
      assert(intersection.apply(15))
      assert(intersection.apply(13))
      assert(!intersection.apply(7))
   }

   @Test
   def upper_union() {
      val ub1 = new UpperBorder[Double](7.0) // (...,7]
      val ub2 = new UpperBorder[Double](13.0) // (...,13]
      val union = ub1 + ub2
      assert(union.apply(8))
      assert(union.apply(13))
      assert(!union.apply(15))
   }

   //   @Test
   //   def intersection() {
   //      {
   //         val lb1 = new LowerBorder[Int](4) // [4,...)
   //         val ub2 = new UpperBorder[Int](22) // (...,22]
   //         val interval = lb1 * ub2
   //         assert(!interval.contains(3))
   //         assert(interval.contains(4))
   //         assert(interval.contains(10))
   //         assert(interval.contains(22))
   //         assert(!interval.contains(300))
   //      }
   //   }

   @Test
   def interval_signum() {
      {
         val ub1 = new UpperBorder[Double](7.0) // (...,7]
         val ub2 = new UpperBorder[Double](13.0) // (...,13]

         {
            val interval = ub2 - ub1
            assert(6.0 == interval.signum, "signum=" + interval.signum)
         }
         {
            val interval = Interval(ub1, ub2)
            assert(6.0 == interval.signum, "signum=" + interval.signum)
         }
         {
            val interval = Interval(ub2, ub1)
            assert(0.0 == interval.signum, "signum=" + interval.signum)
         }
      }
      {
         val lb1 = new LowerBorder[Float](152.25456f) // [152.25456, ...)
         val lb2 = new LowerBorder[Float](6955.2863f) // [6955.2863, ...)

         {
            val interval = lb1 - lb2
            assert(6803.0317f == interval.signum, "signum=" + interval.signum)
         }
         {
            val interval = Interval(lb1, lb2)
            assert(0.0f == interval.signum, interval.signum)
         }
         {
            val interval = Interval(lb2, lb1)
            assert(6803.0317f == interval.signum, interval.signum)
         }
      }
   }

   @Test
   def interval_contains() {
      {
         val ub1 = new UpperBorder[Double](7.0) // (...,7]
         val ub2 = new UpperBorder[Double](13.0) // (...,13]
         val interval = ub2 - ub1 // Interval(ub1, ub2)
         assert(!interval.contains(300))
         assert(interval.contains(13))
         assert(interval.contains(10))
         assert(!interval.contains(7))
         assert(!interval.contains(-3))
      }
      {
         val ub1 = new UpperBorder[Double](7.0) // (...,7]
         val ub2 = new UpperBorder[Double](13.0) // (...,13]
         val interval = ub1 - ub2 //Interval(ub2, ub1)
         assert(!interval.contains(300))
         assert(!interval.contains(13))
         assert(!interval.contains(10))
         assert(!interval.contains(7))
         assert(!interval.contains(-3))
         //         assert(Interval.empty[Double] == interval)
      }
      {
         val lb1 = new LowerBorder[Int](4) // [4,...)
         val lb2 = new LowerBorder[Int](22) // [22,...)
         val interval = lb1 - lb2 //Interval(ub2, ub1)
         assert(!interval.contains(-3))
         assert(interval.contains(4))
         assert(interval.contains(10))
         assert(!interval.contains(22))
         assert(!interval.contains(300))
      }
      {
         val lb1 = new LowerBorder[Int](4) // [4,...)
         val lb2 = new LowerBorder[Int](22) // [22,...)
         val interval = lb2 - lb1 //Interval(ub1, ub2)
         assert(!interval.contains(-3))
         assert(!interval.contains(4))
         assert(!interval.contains(10))
         assert(!interval.contains(22))
         assert(!interval.contains(300))
      }
   }

   @Test
   def interval_toString() {
      {
         val ub1 = new UpperBorder[Double](7.0) // (...,7]
         val ub2 = new UpperBorder[Double](13.0) // (...,13]
         assert("7.0]" == ub1.toString, ub1.toString())
         assert("(7.0" == (-ub1).toString, (-ub1).toString())
         assert("13.0]" == ub2.toString, ub2.toString())
         assert("(13.0" == (-ub2).toString, (-ub2).toString())
         val interval = ub2 - ub1
         assert("(7.0,13.0]" == interval.toString, interval.toString)
      }
      {
         val ub1 = new UpperBorder[Double](7.0) // (...,7]
         val ub2 = new UpperBorder[Double](13.0) // (...,13]
         val interval = ub1 - ub2
         assert("Ø" == interval.toString, interval.toString)
      }
      {
         val lb1 = new LowerBorder[Int](4) // [4,...)
         val lb2 = new LowerBorder[Int](22) // [22,...)
         assert("[4" == lb1.toString, lb1.toString())
         assert("4)" == (-lb1).toString, (-lb1).toString())
         assert("[22" == lb2.toString, lb2.toString())
         assert("22)" == (-lb2).toString, (-lb2).toString())
         val interval = (lb1 - lb2)
         assert("[4,22)" == interval.toString, interval.toString)
      }
      {
         val lb1 = new LowerBorder[Int](4) // [4,...)
         val lb2 = new LowerBorder[Int](22) // [22,...)
         val interval = (lb2 - lb1)
         assert("Ø" == interval.toString, interval.toString)
      }
   }

   @Test
   def IntervalAsIntersection() {
      val lb1 = new LowerBorder[Int](4) // [4,...)
      val lb2 = new LowerBorder[Int](22) // [22,...)
      val ub1 = new UpperBorder[Int](7) // (...,7]
      val ub2 = new UpperBorder[Int](13) // (...,13]

      {
         val interval = new IntervalAsIntersection[Int](lb1, ub1) // [4,7]
         assert("[4,7]" == interval.toString, interval.toString)
         assert(!interval.contains(3))
         assert(interval.contains(4))
         assert(interval.contains(5))
         assert(interval.contains(6))
         assert(interval.contains(7))
         assert(!interval.contains(8))
         assert(3 == interval.signum, "signum=" + interval.signum)
      }

      {
         val interval = new IntervalAsIntersection[Int](lb1, -lb2) // [4,22)
         assert("[4,22)" == interval.toString, interval.toString)
         assert(!interval.contains(3))
         assert(interval.contains(4))
         assert(interval.contains(21))
         assert(!interval.contains(22))
         assert(18 == interval.signum, "signum=" + interval.signum)
      }

      {
         val interval = new IntervalAsIntersection[Int](-ub1, -lb2) // (7,22)
         assert("(7,22)" == interval.toString, interval.toString)
         assert(!interval.contains(7))
         assert(interval.contains(8))
         assert(interval.contains(21))
         assert(!interval.contains(22))
         assert(15 == interval.signum, "signum=" + interval.signum)
      }

      {
         val interval = new IntervalAsIntersection[Int](-ub1, ub2) // (7,13]
         assert("(7,13]" == interval.toString, interval.toString)
         assert(!interval.contains(7))
         assert(interval.contains(8))
         assert(interval.contains(13))
         assert(!interval.contains(24))
         assert(6 == interval.signum, "signum=" + interval.signum)
      }
   }
}
