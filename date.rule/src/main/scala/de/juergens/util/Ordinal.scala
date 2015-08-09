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

  implicit def ordinal2Int(x:Ordinal) : Int = x.toInt
  implicit def ordinal2Long(x:Ordinal) : Long = x.toInt

  def ordinal(i :Int) : String = {
    val sufixes = Array[String]( "th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th" )
    (i % 100) match {
      case 11 | 12 | 14 => i + "th"
      case _ => i + sufixes(i % 10)
    }

  }

  implicit object OrdinalNumeric extends math.Numeric[Ordinal] {
    val num = math.Numeric.IntIsIntegral
    def plus(x: Ordinal, y: Ordinal) =  Ordinal(num.plus(x,y))
    def minus(x: Ordinal, y: Ordinal) = Ordinal(num.minus(x,y))
    def times(x: Ordinal, y: Ordinal) = Ordinal(num.times(x,y))
    def negate(x: Ordinal): Ordinal =   Ordinal(num.negate(x))
    def fromInt(x: Int) = Ordinal(x)
    def toInt(x: Ordinal) = x
    def toLong(x: Ordinal) = toInt(x)
    def toFloat(x: Ordinal) = toInt(x)
    def toDouble(x: Ordinal) = toInt(x)
    def compare(x:Ordinal,y:Ordinal) = num.compare(x,y)
  }
  assert(Down * Ordinal(1) == Ordinal(-1))
}

case class Ordinal(toInt : Int) extends AnyVal {
  def unary_- : Ordinal = Ordinal(-toInt)

  override def toString: String = toInt.toString + "."
}

