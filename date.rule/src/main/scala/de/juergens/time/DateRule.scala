/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.juergens.time

import java.time.LocalDate
import java.time.temporal.{ChronoField, Temporal}

import de.juergens.rule.Predicate
import de.juergens.time.impl.DateShifter

sealed abstract class DateRule {
  def evaluate(anchor: LocalDate)(t: Temporal): Boolean

  /*
  protected def makePredicate(anchor:Date) = new Predicate[Date] {
    override def evaluate(t: Date): Boolean = evaluate(anchor)(t)
  }
  */

  def test(anchor: LocalDate): Predicate[Temporal] = evaluate(anchor) _

  def date(anchor: LocalDate, calendar: Calendar): LocalDate = {
    val shifter = new DateShifter(test(anchor))
    shifter.next(anchor)
  }
}

case class ShiftRule(shifter: Shifter) extends DateRule {
  def evaluate(anchor: LocalDate)(t: Temporal): Boolean = {
    val date = shifter.shift(anchor)
    t == date
  }

  override def toString = shifter.toString
}

case class FixRule(number: Int, component: DateComponent) extends DateRule {
  def evaluate(ignored: LocalDate)(t: Temporal): Boolean = {
    val date = LocalDate.from(t)

    component match {
      case de.juergens.time.Apr => date.get(ChronoField.MONTH_OF_YEAR) == number
    }
  }

  override def toString = number + " " + component.toString
}

case class UnionRule(rules: List[DateRule]) extends DateRule {
  def evaluate(anchor: LocalDate)(t: Temporal): Boolean = rules.foldLeft(false)((b, r) => b | r.evaluate(anchor)(t))

  override def toString = rules.mkString("|")
}

case class IntersectionRule(rules: List[DateRule]) extends DateRule {
  def evaluate(anchor: LocalDate)(t: Temporal): Boolean = rules.foldRight(true)((r, b) => b & r.evaluate(anchor)(t))

  override def toString = rules.mkString("&")
}

case class ListRule(rules: List[DateRule]) extends DateRule {
  def evaluate(anchor: LocalDate)(t: Temporal): Boolean = {
    t == rules.foldLeft(anchor)((d, s) => s.date(d, null))
  }

  override def toString = rules.mkString("(", ">", ")")
}
