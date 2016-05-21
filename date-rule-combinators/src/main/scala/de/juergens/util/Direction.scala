package de.juergens.util


abstract sealed class Direction {
  def *[T](x: T)(implicit numeric : Numeric[T]) : T
}

case object Up extends Direction {
  def unary_~ : Direction = Down
  def *[T](x: T)(implicit numeric : Numeric[T]) : T = x
}
case object Down extends Direction {
  def unary_~ : Direction = Up
  def *[T](x: T)(implicit numeric : Numeric[T]) : T = numeric.negate(x)
}

/**
 * Created by juergens on 24.05.15.
 */
object Direction {
  def fromStr(x : String) : Direction = x.toLowerCase match {
    case "next" | "after"  => Up
    case "previous" | "before" | "prior" => Down
  }

  def fromNumber(x : Any) : Direction = x match {
    case x : Int  if x>0 => Up
    case x : Int  if x<0 => Down
    case x : Long  if x>0 => Up
    case x : Long  if x<0 => Down
  }

  def apply(x : Any) : Direction = x match {
    case str : String => fromStr(str)
    case x => fromNumber(x)
  }
}
