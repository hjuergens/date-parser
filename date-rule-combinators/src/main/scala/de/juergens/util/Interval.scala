/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.juergens.util

import scala.math.Ordered._
import scala.math._

abstract sealed class Border[T] {
  type S <: Border[T]
  type O <: Border[T]

  def include : Boolean

  final def apply(lhs : T, rhs:T)
                 (implicit ord : scala.math.Ordering[T]) : Boolean
  = if(include) lhs <= rhs else lhs < rhs

  /**intersection */
  def *(that: S) : S

  /**intersection */
  def *(that: O) : Interval[T]

  /**union */
  def +(that: S) :S

  /**complement ; all elements which are members of this but not members of rhs*/
  def -(that: S) : Interval[T]

  /**absolute complement */
  def unary_-() : O
}

/**
 * lower bound
 * @param lhs left hand side
 * @param include closed
 * @param ord ordering on type T
 * @tparam T
 */
case class LowerBorder[T](lhs:T, include:Boolean = true)
                         (implicit ord : scala.math.Ordering[T])
  extends Border[T] {

  type S = LowerBorder[T]
  type O = UpperBorder[T]

  def apply(rhs: T): Boolean = apply(lhs, rhs)

  implicit val ordBorders = Ordering.fromLessThan(
    (a:LowerBorder[T],b: LowerBorder[T]) =>
      if (a.lhs < b.lhs) false
      else if (a.lhs > b.lhs) true
      else {
        if (a.include && !b.include) false
        else if (!a.include && b.include) true
        else false
      })

  /** intersection */
  override def *(that: LowerBorder[T]): LowerBorder[T] = {
    import Ordering.Implicits.infixOrderingOps
    this min that
  }

  /** intersection */
  override def *(that: UpperBorder[T]): Interval[T] =
    Interval(this, that)

  /** union */
  override def +(that: LowerBorder[T]): LowerBorder[T] = {
    import Ordering.Implicits.infixOrderingOps
    this max that
  }

  /** complement ; all elements which are members of this but not members of rhs */
  override def -(that: LowerBorder[T]): Interval[T] =
    Interval(this, -that)

  /** absolute complement */
  override def unary_-(): UpperBorder[T] =
    new UpperBorder[T](lhs, !include)

  override def toString = (if (include) "[" else "(") + lhs.toString
}

/**
 * upper bound
 * @param rhs right hand side
 * @param include closed
 * @param ord ordering on type T
 * @tparam T
 */
case class UpperBorder[T](rhs:T, include:Boolean= true)
                         (implicit ord : scala.math.Ordering[T])
  extends Border[T] {

  type S = UpperBorder[T]
  type O = LowerBorder[T]

  def apply(lhs: T): Boolean = apply(lhs, rhs)

  implicit val ordBorders = Ordering.fromLessThan(
    (a:UpperBorder[T],b: UpperBorder[T]) =>
      if (a.rhs < b.rhs) true
      else if (a.rhs > b.rhs) false
      else {
        if (a.include && !b.include) false
        else if (!a.include && b.include) true
        else false
      })

  /** intersection */
  override def *(that: UpperBorder[T]): UpperBorder[T] = {
    import Ordering.Implicits.infixOrderingOps
    this min that
  }


  /** intersection */
  override def *(that: LowerBorder[T]): Interval[T] =
    Interval(that,this)

  /** union */
  override def +(that: UpperBorder[T]): UpperBorder[T] = {
    import Ordering.Implicits.infixOrderingOps
    this max that
  }

  /** complement ; all elements which are members of this but not members of that */
  override def -(that: UpperBorder[T]) : Interval[T] =
    Interval(-that, this)

  /** absolute complement */
  override def unary_-(): LowerBorder[T] =
    new LowerBorder[T](rhs, !include)

  override def toString =
    rhs.toString + (if (include) "]" else ")")
}

object Interval {
  def unapply[T](impl: IntervalImpl[T]): Option[(LowerBorder[T], UpperBorder[T])]
  = Some((impl.lower, impl.upper))

  def apply[T](lower:LowerBorder[T], upper:UpperBorder[T])(implicit ord : scala.math.Ordering[T])
  = new IntervalImpl(lower, upper)
  def apply[T](lower:UpperBorder[T], upper:UpperBorder[T])(implicit ord : scala.math.Ordering[T])
  = new IntervalImpl(-lower, upper)
  def apply[T](lower:LowerBorder[T], upper:LowerBorder[T])(implicit ord : scala.math.Ordering[T])
  = new IntervalImpl(lower, -upper)

  def apply[T](lower:T, upper:T)
              (implicit ord : scala.math.Ordering[T]) : IntervalImpl[T]
  = apply[T](lower,true,upper,false)

  def apply[T](lower:T, includeLower: Boolean, upper:T, includeUpper:Boolean)
              (implicit ord : scala.math.Ordering[T]) : IntervalImpl[T]
  = new IntervalImpl(LowerBorder[T](lower,includeLower), UpperBorder[T](upper,includeUpper))
}

trait Interval[T] {
  type I <: Interval[T]

  /**measure */
  def signum(implicit num:Numeric[T]) :T

  def contains(t:T): Boolean

  /**union */
  def +(that: I) : I

  /**complement ; all elements which are members of this but not members of rhs*/
  def -(that: I) : I

  /**intersection */
  def *(that: I) : I
}

/**
 * Implementation of Interval
 * @param lower left border
 * @param upper right border
 * @param ord
 * @tparam T
 */
case class IntervalImpl[T](lower: LowerBorder[T], upper: UpperBorder[T])
                          (implicit ord : scala.math.Ordering[T])
  extends Interval[T] {

  type I = IntervalImpl[T]

  override def signum(implicit num:Numeric[T]) :T = num.minus(upper.rhs,lower.lhs)

  /**union */
  def +(that: IntervalImpl[T]) : IntervalImpl[T] = {
    if(this.upper.rhs > that.lower.lhs && this.lower.lhs < that.upper.rhs)
      new IntervalImpl[T](this.lower + that.lower, this.upper + that.upper)
    else if(that.upper.rhs > this.lower.lhs && that.lower.lhs < this.upper.rhs)
      new IntervalImpl[T](this.lower + that.lower, this.upper + that.upper)
    else if(this.upper.rhs == that.lower.lhs && (this.upper.include || that.lower.include))
      new IntervalImpl[T](this.lower + that.lower, this.upper + that.upper)
    else if(that.upper.rhs == this.lower.lhs && (that.upper.include || this.lower.include))
      new IntervalImpl[T](this.lower + that.lower, this.upper + that.upper)
    else
      throw new IllegalArgumentException(s"The intervals $this and $that don't touch.")
  }

  /**complement ; all elements which are members of this but not members of rhs*/
  def -(that: IntervalImpl[T]) =
    new IntervalImpl[T](this.lower * (-that.upper), this.upper * (-that.lower))

  /**intersection */
  def *(that: IntervalImpl[T]) =
    if(this.upper.rhs >= that.lower.lhs && this.lower.lhs <= that.upper.rhs)
      new IntervalImpl[T](this.lower*that.lower, this.upper*that.upper)
    else if(that.upper.rhs >= this.lower.lhs && that.lower.lhs <= this.upper.rhs)
      new IntervalImpl[T](this.lower*that.lower, this.upper*that.upper)
    else
      throw new IllegalArgumentException(s"The intervals $this and $that are disjunct.")

  override def contains(t: T): Boolean = lower(t) && upper(t)

  override def toString = lower.toString + "," + upper.toString
}


