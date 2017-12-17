package samples

import org.parboiled.errors.{ErrorUtils, ParsingException}
import org.parboiled.scala._

sealed abstract class AstNode

case class NumberNode(str: String) extends AstNode {
  def value = Integer.parseInt(str)
}
case class FactorNode(text: ExpressionNode) extends AstNode
case class TermNode(text: String) extends AstNode
case class ExpressionNode(text: String) extends AstNode

class SimpleCalculator extends Parser {

  def InputLine: Rule1[Int] = rule { Expression ~ EOI }

  def Expression: Rule1[Int] = rule {
    Term ~ zeroOrMore(
      "+" ~ Term ~~> ((a:Int, b) => a + b)
        | "-" ~ Term ~~> ((a:Int, b) => a - b)
    )
  }

  def Term: Rule1[Int] = rule {
    Factor ~ zeroOrMore(
      "*" ~ Factor ~~> ((a:Int, b) => a * b)
        | "/" ~ Factor ~~> ((a:Int, b) => a / b)
    )
  }

  def Factor: Rule1[Int] = rule { Number | Parens }

  def Parens: Rule1[Int] = rule { "(" ~ Expression ~ ")" }

  def Number: Rule1[Int] = rule { Digits ~> (_.toInt) }

  def Digits: Rule0 = rule { oneOrMore(Digit) }

  def Digit: Rule0 = rule { "0" - "9" }

  /**
    * The main parsing method. Uses a ReportingParseRunner (which only reports the first error) for simplicity.
    */
  def calculate(expression: String): Int = {
    val parsingResult = ReportingParseRunner(InputLine).run(expression)
    parsingResult.result match {
      case Some(i) => i
      case None => throw new ParsingException("Invalid calculation expression:\n" +
        ErrorUtils.printParseErrors(parsingResult))
    }
  }}