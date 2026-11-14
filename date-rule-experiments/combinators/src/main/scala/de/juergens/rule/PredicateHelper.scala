package de.juergens.rule


import java.util.function.Predicate

import scala.language.implicitConversions







object PredicateHelper {
  implicit def predicate2Function[T](p:Predicate[T]) : (T)=>Boolean =
    p.test _

  /*
  implicit class Implementation[T](func: (T) => Boolean)
    extends Predicate[T] {
    override def test(t: T): Boolean = func(t)
  }
  */

}

