package de.juergens.util

import scala.language.implicitConversions

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
    case "five"=> 5
    case "six"=> 6
    case "seven"=> 7
    case "eight"=> 8
    case "nine"=> 9
    case "ten"=> 10
    case "eleven"=> 11
    case "twelve"=> 12
    case "thirteen"=> 13
    case "fourteen"=> 14
    case "fifteen"=> 15
    case "sixteen"=> 16
    case "seventeen"=> 17
    case "eighteen"=> 18
    case "nineteen"=> 19
    case "twenty" => 20
    case s if s endsWith "." => Integer.parseInt(s.substring(0, s.length - 1))
    case s if s startsWith  "#" => Integer.parseInt(s.substring(1, s.length - 1))
    case s if s.forall(_.isDigit) => Integer.parseInt(s)
  }

  /** Extractor object */
  object CardinalText {
    import scala.PartialFunction.condOpt
    def unapply(x: Any): Option[Cardinal] = condOpt(x){k andThen h}.map(Cardinal(_))
  }

  //implicit def int2Cardinal(x:Int) : Cardinal = Cardinal(x)
  implicit def cardinal2Int(x:Cardinal) : Int = x.toInt
}

case class Cardinal(toInt : Int) extends AnyVal {
  def unary_- : Cardinal = Cardinal(-toInt)

  override def toString: String = toInt.toString + "."
}