package de.juergens.rule

   trait Predicate[T] {
      def evaluate(t: T): Boolean
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

