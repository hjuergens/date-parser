package de.juergens.util

/**
 * Created by juergens on 20.05.15.
 */
sealed abstract class Sign {
  override final def toString: String = this match {
    case Sign(name) => name
  }

  def *(i: Int): Int
}

object Plus extends Sign {
  def *(i: Int) = i
}

object Minus extends Sign {
  def *(i: Int) = -i
}

object Sign {
  def apply(str: String): Sign = str match {
    case "+" | "plus" => Plus
    case "-" | "minus" => Minus
  }

  def unapply(unit: Sign): Option[String] = PartialFunction.condOpt(unit) {
    case Plus => "+"
    case Minus => "-"
  }

}

