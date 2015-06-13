package samples


import de.juergens.text.SimpleParser
import org.junit.runner.RunWith
import org.specs2._
import org.specs2.runner.JUnitRunner

import scala.util.parsing.combinator.Parsers


/**
 * Created by juergens on 15.05.15.
 */
@RunWith(classOf[JUnitRunner])
class SimpleParser$Test extends mutable.Specification {
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