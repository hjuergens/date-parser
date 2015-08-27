package de.juergens.rule


import java.util.function.Predicate
import scala.languageFeature.implicitConversions

/**
 * [[Predicate]]
 * @constructor create a predicates as or composition
 * @param predicates any predicates on type T
 * @tparam T
 */
case class UnionPredicate[T](predicates: Predicate[T]*) extends Predicate[T] {

  /**
   * Checks if any of the predicates matches for the specified object.
   * @param t an object to test
   * @return or-composition
   */
  def test(t: T) = predicates.foldLeft(false)((b, r) => b | r.test(t))

  override def toString = predicates.mkString("|")
}

/**
 * [[Predicate]]
 * @constructor create a predicates as and composition
 * @param predicates tany predicates on type T
 * @tparam T
 */
case class IntersectionPredicate[T](predicates: Predicate[T]*) extends Predicate[T] {

  /**
   * Checks if all of the predicates matches for the specified object.
   * @param t an object to test
   * @return and-composition
   */
  def test(t: T) = predicates.foldRight(true)((r, b) => b & r.test(t))

  override def toString = predicates.mkString("&")
}

case class NotPredicate[T](predicate: Predicate[T]) extends Predicate[T] {
  def test(t: T) = !predicate.test(t)

  override def toString = "NOT" + predicate
}

object PredicateHelper {
  implicit def predicate2Function[T](p:Predicate[T]) : (T)=>Boolean =
    p.test _

  implicit class Implementation[T](func: (T) => Boolean)
    extends Predicate[T] {
    override def test(t: T): Boolean = func(t)
  }

}

