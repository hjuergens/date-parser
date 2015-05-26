package de.juergens.util

/**
 * Created by juergens on 25.05.15.
 */
object Ordinal {
  def fromString(str: String) : Ordinal = { val OrdinalText(ord) = str; ord}

  private val k : PartialFunction[Any,String] = { case str : String => str }
  private val h : PartialFunction[String,Int] = {
    case "second" => 2
    case s if s endsWith "." => Integer.parseInt(s.substring(0, s.length - 1))
  }

  /** Extractor object */
  object OrdinalText {
    import scala.PartialFunction.condOpt
    def unapply(x: Any): Option[Ordinal] = condOpt(x){k andThen h}.map(Ordinal(_))
  }

  implicit def int2Ordinal(x:Int) : Ordinal = Ordinal(x)
}

case class Ordinal(toInt : Int) extends AnyVal {
  def unary_- : Ordinal = Ordinal(-toInt)

  override def toString: String = toInt.toString + "."
}
