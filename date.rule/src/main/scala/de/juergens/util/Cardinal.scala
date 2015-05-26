package de.juergens.util

/**
 * Created by juergens on 25.05.15.
 */
object Cardinal {
  def fromString(str: String) : Cardinal = { val CardinalText(ord) = str; ord}

  private val k : PartialFunction[Any,String] = { case str : String => str }
  private val h : PartialFunction[String,Int] = {
    case "one" => 1
    case "two" => 2
    case "three" => 3
    case "four" => 4
    case s if s endsWith "." => Integer.parseInt(s.substring(0, s.length - 1))
  }

  /** Extractor object */
  object CardinalText {
    import scala.PartialFunction.condOpt
    def unapply(x: Any): Option[Cardinal] = condOpt(x){k andThen h}.map(Cardinal(_))
  }

  implicit def int2Cardinal(x:Int) : Cardinal = Cardinal(x)
}

case class Cardinal(toInt : Int) extends AnyVal {
  def unary_- : Cardinal = Cardinal(-toInt)

  override def toString: String = toInt.toString + "."
}
