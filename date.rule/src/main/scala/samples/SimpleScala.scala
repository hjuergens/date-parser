package samples

import scala.language.postfixOps
import scala.util.parsing.combinator.RegexParsers

// TODO check out this example
object SimpleScala extends RegexParsers {

  val ID = """[a-zA-Z]([a-zA-Z0-9]|_[a-zA-Z0-9])*""" r

  val NUM = """[1-9][0-9]*""" r

  def program = clazz *

  def classPrefix = "class" ~ ID ~ "(" ~ formals ~ ")"

  def classExt = "extends" ~ ID ~ "(" ~ actuals ~ ")"

  def clazz = classPrefix ~ opt(classExt) ~ "{" ~ (member *) ~ "}"

  def formals = repsep(ID ~ ":" ~ ID, ",")

  def actuals = expr *

  def member = (
    "val" ~ ID ~ ":" ~ ID ~ "=" ~ expr
      | "var" ~ ID ~ ":" ~ ID ~ "=" ~ expr
      | "def" ~ ID ~ "(" ~ formals ~ ")" ~ ":" ~ ID ~ "=" ~ expr
      | "def" ~ ID ~ ":" ~ ID ~ "=" ~ expr
      | "type" ~ ID ~ "=" ~ ID
    )

  def expr: Parser[Any] = factor ~ (
    "+" ~ factor
      | "-" ~ factor
    ) *

  def factor = term ~ ("." ~ ID ~ "(" ~ actuals ~ ")") *

  def term = (
    "(" ~ expr ~ ")"
      | ID
      | NUM
    )
}