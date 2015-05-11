/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.juergens.time

abstract class DateRule {
  def evaluate(anchor: Date, t: Date): Boolean

  def date(anchor: Date, calendar: Calendar): Date = {
    def predicate(t: Date) = evaluate(anchor, t)
    val shifter = new DateShifter(predicate)
    shifter.next(anchor)
  }
}

case class ShiftRule(shifter: Shifter) extends DateRule {
  def evaluate(anchor: Date, t: Date): Boolean = {
    val date = shifter.shift(anchor)
    t == date
  }

  override def toString = shifter.toString
}

case class FixRule(number: Int, component: DateComponent) extends DateRule {
  def evaluate(anchor: Date, t: Date): Boolean = {
    t == component
  }

  override def toString = number + " " + component.toString
}

case class UnionRule(rules: List[DateRule]) extends DateRule {
  def evaluate(anchor: Date, t: Date): Boolean = rules.foldLeft(false)((b, r) => b | r.evaluate(anchor, t))

  override def toString = rules.mkString("|")
}

case class IntersectionRule(rules: List[DateRule]) extends DateRule {
  def evaluate(anchor: Date, t: Date): Boolean = rules.foldRight(true)((r, b) => b & r.evaluate(anchor, t))

  override def toString = rules.mkString("&")
}

case class ListRule(rules: List[DateRule]) extends DateRule {
  def evaluate(anchor: Date, t: Date): Boolean = {
    t == rules.foldLeft(anchor)((d, s) => s.date(d, null))
  }
    override def toString = rules.mkString("(", ">", ")")
}
