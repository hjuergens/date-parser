/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.juergens.util

import scala.math._

/**
 */
object `package` {
   //   implicit def fromInt(i: Int) = Num(i)
}

sealed abstract class Comparator[A <% Ordered[A]] extends ((A, A) => Boolean) {
   def unary_!(): Comparator[A]

   def toBorderString(a: A): String

   def signum(implicit n: Numeric[A]): A
}

object Comparator {
}

/**less or equal */
class Leq[@specialized(Int, Double) A <% Ordered[A]] extends Comparator[A] {
   def apply(l: A, r: A) = l <= r

   /**greater than */
   private val GT = new Comparator[A] {
      def apply(a1: A, a2: A) = a1 > a2

      def unary_!(): Comparator[A] = Leq.this

      def toBorderString(a: A) = a + ")"

      def signum(implicit n: Numeric[A]) = n.negate(Leq.this.signum)
   }

   def unary_!() : Comparator[A] = GT

   def toBorderString(a: A) = "[" + a

   final def signum(implicit n: Numeric[A]): A = n.negate(n.one)
}

/**greater or equal */
class Geq[@specialized(Int, Double) A <% Ordered[A]] extends Comparator[A] {
   def apply(l: A, r: A) = l >= r

   /**less than */
   private val LT = new Comparator[A] {
      def apply(a1: A, a2: A) = a1 < a2

      def unary_!(): Comparator[A] = Geq.this

      def toBorderString(a: A) = "(" + a

      def signum(implicit n: Numeric[A]) = n.negate(Geq.this.signum)
   }


   def unary_!() : Comparator[A] = LT

   def toBorderString(a: A) = a + "]"

   final override def signum(implicit n: Numeric[A]): A = n.one
}

abstract class Border[@specialized(Int, Double) T](comparator: Comparator[T]) /*extends Ordered[Border[T]]*/ {
   def point: T

   def apply(t: T) = comparator(point, t)

   final def contains(t: T) = this(t)

   /**absolute complement */
   def unary_-() = new Border[T](!comparator) {
      def point: T = Border.this.point

      //def compare(that: Border[T]) = that.compare(this)
   }

   //   /**intersection */
   //   def *(rhs: Border[T]): Border[T] = if (this > rhs) this else rhs
   //
   //   /**union */
   //   def +(rhs: Border[T]): Border[T] = if (this < rhs) this else rhs

   override final def toString = comparator.toBorderString(point)

   final def signum(implicit n: Numeric[T]) = n.times(comparator.signum, point)
}

class LowerBorder[T <% Ordered[T]](val point: T) extends Border[T](new Leq[T]()) with Ordered[LowerBorder[T]] {
   def compare(that: LowerBorder[T]): Int = this.point compare that.point

   /**intersection */
   def *(rhs: LowerBorder[T]) = if (point > rhs.point) this else rhs

   //   def *(rhs: UpperBorder[T]) = Interval(this, -rhs.asInstanceOf[LowerBorder[T]])

   /**union */
   def +(rhs: LowerBorder[T]) = if (point < rhs.point) this else rhs

   /**complement ; all elements which are members of this but not members of rhs*/
   def -(rhs: LowerBorder[T]) = /*if(this.point >= rhs.point) new EmptyInterval[T] else*/ Interval(rhs, this)
}

class UpperBorder[T <% Ordered[T]](val point: T) extends Border[T](new Geq[T]()) with Ordered[UpperBorder[T]] {
   def compare(that: UpperBorder[T]): Int = that.point compare this.point

   /**intersection */
   def *(rhs: UpperBorder[T]) = if (point < rhs.point) this else rhs

   /**union */
   def +(rhs: UpperBorder[T]) = if (point > rhs.point) this else rhs

   /**complement ; all elements which are members of this but not members of rhs*/
   def -(rhs: UpperBorder[T]) = /*if(this.point >= rhs.point) new EmptyInterval[T] else*/ Interval(rhs, this)
}

//class UnionBorder[@specialized(Int, Double) T <% Ordered[T]](lowerBorders: Traversable[LowerBorder[T]], upperBorders: Traversable[UpperBorder[T]]) {
//   val lowerBorder = lowerBorders.min
//   val upperBorder = upperBorders.max
//}
//
//class IntersectBorder[@specialized(Int, Double) T <% Ordered[T]](lowerBorders: Traversable[LowerBorder[T]], upperBorders: Traversable[UpperBorder[T]]) {
//   val lowerBorder = lowerBorders.max
//   val upperBorder = upperBorders.min
//}

abstract class Interval[@specialized(Int, Double) T <% Ordered[T]] {
   val b1: Border[T]
   val b2: Border[T]

   //   assert(b2.point < b1.point)

   /*final*/
   def signum(implicit n: Numeric[T]) = n.minus(b2.point, b1.point) // b2.point - b1.point

   /*final*/
   def contains(v: T) = b2(v) && !b1(v)

   override def toString =
      if (b1.point < b2.point)
         (-b1) + "," + b2
      else if (b2.point < b1.point)
         b2 + "," + (-b1)
      else "Ø"
}

class IntervalAsIntersection[T <% Ordered[T]](val b: Border[T]*) extends Interval[T] {
   val b1 = null
   val b2 = null

   /*final*/
   override def signum(implicit n: Numeric[T]) = (b).map(_.signum).sum(n) // n.minus(b2.point, b1.point)

   /*final*/
   override def contains(v: T) = (b).forall(_(v))

   override def toString = (b).toList.sortWith(_.point < _.point).mkString(",")
}

private final class EmptyInterval[T <% Ordered[T]] extends Interval[T] {
   val b1 = null
   val b2 = null

   override def contains(v: T) = false

   override def signum(implicit n: Numeric[T]) = n.fromInt(0)

   override def toString = "Ø"
}

object Interval {
   //   class IntervalImpl[@specialized(Int, Double) T <% Ordered[T]](val b1: Border[T], val b2: Border[T]) extends Interval

   def apply[T <% Ordered[T]](a: LowerBorder[T], b: LowerBorder[T]) = {
      if (b < a) new IntervalAsIntersection[T](b, -a /*-a,b*/) else new EmptyInterval[T]()
      //      if (b < a) new Interval {
      //         val b1 = a
      //         val b2 = b
      //      } else new EmptyInterval[T]()
   }

   def apply[T <% Ordered[T]](a: UpperBorder[T], b: UpperBorder[T]) = {
      if (b < a) new IntervalAsIntersection[T](-a, b) else new EmptyInterval[T]()
      //      if (b < a) new Interval {
      //         val b1 = a
      //         val b2 = b
      //      } else new EmptyInterval[T]()
   }

   def empty[T <% Ordered[T]]: Interval[T] = new EmptyInterval[T]()

   //   def empty[Double] : Interval[Double] = new EmptyInterval[Double]()
}

abstract class IntervalImp[T <: Ordered[T]](val b1: Border[T], val b2: Border[T]) extends Interval[T] {
   /**union */
   def +(rhs: Interval[T])

   /**complement ; all elements which are members of this but not members of rhs*/
   def -(rhs: Interval[T])

   /**intersection */
   def *(rhs: Interval[T])

   /**? */
   def /(rhs: Interval[T])

   /**symmetric difference */
   def ^(rhs: Interval[T])

   /**absolute complement */
   def unary_-()

   /**measure */
   def abs(): Double

   /**direction */
   def signum(): Int
}


