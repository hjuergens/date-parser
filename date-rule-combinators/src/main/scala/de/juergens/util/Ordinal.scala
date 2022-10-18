package de.juergens.util

import scala.language.implicitConversions

/**
 * Created by juergens on 25.05.15.
 */
object Ordinal {
  val string2Int : PartialFunction[String,Int] = {
    case "first" => 1
    case "second" => 2
    case "third" => 3
    case "eleventh" => 11
    case "twelfth" | "twelfe" | "twelveth" => 12
    case "thirteenth" => 13
    case "fourth" => 4
    case "fourteenth" => 14
    case "fifth" => 5
    case "fifteenth" => 15
    case "sixth" => 6
    case "sixteenth" => 16
    case "seventh " => 7
    case "seventeenth" => 17
    case "eighth" => 8
    case "eighteenth" => 18
    case "ninth" => 9
    case "nineteenth" => 19
    case "tenth" => 10
    case "twentieth" => 20
  }

  def fromString(str: String) : Ordinal = { val OrdinalText(ord) = str; ord}

  private val k : PartialFunction[Any,String] = { case str : String => str }

  /** Extractor object */
  object OrdinalText {
    import scala.PartialFunction.condOpt
    def unapply(x: Any): Option[Ordinal] = condOpt(x){k andThen string2Int}.map(Ordinal(_))
  }

  implicit def ordinal2Int(x:Ordinal) : Int = x.toInt
  implicit def ordinal2Long(x:Ordinal) : Long = x.toInt

  def ordinal(i :Int) : String = {
    val suffixes = Array[String]( "th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th" )
    (i % 100) match {
      case 11 | 12 | 14 => i + "th"
      case _ => i + suffixes(i % 10)
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

    override def parseString(str: String): Option[Ordinal] = { val OrdinalText(ord) = str; Some(ord) }
  }
  assert(Down * Ordinal(1) == Ordinal(-1))
}

case class Ordinal(toInt : Int) extends AnyVal {
  def abs: Ordinal = Ordinal(Math.abs(toInt))

  def unary_- : Ordinal = Ordinal(-toInt)

  override def toString: String = Ordinal.ordinal(toInt)
}

