package de.juergens.text

import org.specs2.mutable.Specification
import scala.util.parsing.combinator.Parsers


/**
 * Created by juergens on 15.05.15.
 */
class SimpleParser$Test extends Specification {
  "Parsing the word 'world' should" >> {
    "lead to success" in {
      SimpleParser.parse(SimpleParser.word, "world") must haveClass[Parsers#Success[String]]
    }
    "result in 'world' " in {
      SimpleParser.parse(SimpleParser.word, "world").get must be_==("world")
    }
    "be parsed successfully" in {
      SimpleParser.parse(SimpleParser.word, "world").successful isSuccess
    }
  }}