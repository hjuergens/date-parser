package de.juergens.util

import scala.language.implicitConversions

/**
 * Created by juergens on 25.05.15.
 */
object Ordinal {
  def fromString(str: String) : Ordinal = { val OrdinalText(ord) = str; ord}

  private val k : PartialFunction[Any,String] = { case str : String => str }
  private val h : PartialFunction[String,Int] = {
    case "first" => 1
    case "second" => 2
    case "third" => 3
    case s if s endsWith "." => Integer.parseInt(s.substring(0, s.length - 1))
  }

  /** Extractor object */
  object OrdinalText {
    import scala.PartialFunction.condOpt
    def unapply(x: Any): Option[Ordinal] = condOpt(x){k andThen h}.map(Ordinal(_))
  }

  implicit def int2Ordinal(x:Int) : Ordinal = Ordinal(x)

  def ordinal(i :Int) : String = {
    val sufixes = Array[String]( "th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th" )
    (i % 100) match {
      case 11 | 12 | 14 => i + "th"
      case _ => i + sufixes(i % 10)
    }

  }
}

case class Ordinal(toInt : Int) extends AnyVal {
  def unary_- : Ordinal = Ordinal(-toInt)

  override def toString: String = toInt.toString + "."
}

