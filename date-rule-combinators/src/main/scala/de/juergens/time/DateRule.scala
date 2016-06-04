/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.juergens.time

import java.time.LocalDate
import java.time.temporal.{ChronoField, Temporal, TemporalAccessor}
import java.util.function.Predicate

import xuwei_k.Scala2Java8.predicate
import de.juergens.rule.PredicateHelper._
import de.juergens.time.impl.DateShifter

import scala.language.implicitConversions

sealed abstract class DateRule {
  def evaluate(anchor: LocalDate)(t: Temporal): Boolean

  def test(anchor: LocalDate): Predicate[Temporal] = evaluate(anchor) _

  def date(anchor: LocalDate, calendar: Calendar): LocalDate = {
    val shifter = new DateShifter(test(anchor))
    LocalDate.from(shifter.adjustInto(anchor))
  }
}

case class ShiftRule(shifter: LocalDateAdjuster) extends DateRule {
  def evaluate(anchor: LocalDate)(t: Temporal): Boolean = {
    val date = shifter.apply(anchor)
    t == date
  }

  override def toString = shifter.toString
}

case class FixRule(number: Int, component: TemporalAccessor) extends DateRule {
  def evaluate(ignored: LocalDate)(t: Temporal): Boolean = {
    val date = LocalDate.from(t)

    component match {
      case java.time.Month.APRIL => date.get(ChronoField.MONTH_OF_YEAR) == number
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
