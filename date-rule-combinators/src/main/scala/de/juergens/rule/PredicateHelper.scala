package de.juergens.rule


import java.util.function.Predicate
import scala.languageFeature.implicitConversions

case class UnionPredicate[T](predicates: Predicate[T]*) extends Predicate[T] {
  def test(t: T) = predicates.foldLeft(false)((b, r) => b | r.test(t))

  override def toString = predicates.mkString("|")
}

case class IntersectionPredicate[T](predicates: Predicate[T]*) extends Predicate[T] {
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

