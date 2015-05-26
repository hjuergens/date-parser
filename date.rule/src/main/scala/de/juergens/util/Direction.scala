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
  def apply(x : Any) : Direction = x match {
    case "Next" | "next" | "After" | "after" => Up
    case "Previous" | "before" => Down
    case x : Int  if x>0 => Up
    case x : Int  if x<0 => Down
  }
}
