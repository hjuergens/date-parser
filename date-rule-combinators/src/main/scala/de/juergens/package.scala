package de

import java.util.function.UnaryOperator

/**
 * Created by juergens on 16.08.15.
 */
package object juergens {
  implicit def unaryOperator[T](function:(T)=>T) : UnaryOperator[T] =
    new UnaryOperator[T] {
      override def apply(t: T): T = function(t)
    }
}
