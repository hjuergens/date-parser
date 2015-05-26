package de.juergens.rule


import java.util.function

trait Predicate[-T] extends ((T)=>Boolean) {
      def evaluate(t: T): Boolean
  final def apply(t:T) = evaluate(t)
   }

   case class UnionPredicate[T](predicates : Predicate[T]*) extends Predicate[T] {
      def evaluate(t: T): Boolean = predicates.foldLeft(false)((b, r) => b | r.evaluate(t))

      override def toString = predicates.mkString("|")
   }

   case class IntersectionPredicate[T] (predicates : Predicate[T]*) extends Predicate[T] {
      def evaluate(t: T): Boolean = predicates.foldRight(true)((r, b) => b & r.evaluate(t))

      override def toString = predicates.mkString("&")
   }

   case class NotPredicate[T] (predicate : Predicate[T]) extends Predicate[T] {
      def evaluate(t: T): Boolean = !predicate.evaluate(t)

      override def toString = "NOT" + predicate
   }

object Predicate {
  import java.util.function.{Predicate => JPredicate}
  implicit class Implementation[T](func:(T)=>Boolean) extends JPredicate[T] with Predicate[T]{
    def test(t: T): Boolean = func(t)

    def evaluate(t: T): Boolean = func(t)

    //def apply(t: T): Boolean = func(t)

    override def and(other: function.Predicate[_ >: T]): function.Predicate[T] = super.and(other)

    override def negate(): function.Predicate[T] = super.negate()

    override def or(other: function.Predicate[_ >: T]): function.Predicate[T] = super.or(other)
  }
}

