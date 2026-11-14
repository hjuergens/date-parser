package samples


import org.junit.runner.RunWith
import org.specs2._


import scala.languageFeature.postfixOps
import scala.util.parsing.combinator.Parsers

/**
 * Created by juergens on 15.05.15.
 */
class SimpleParser$Test extends mutable.Specification {
  "Parsing the word 'world' should" >> {
    "lead to success" in {
      SimpleWordParser.parse(SimpleWordParser.word, "world") must haveClass[Parsers#Success[String]]
    }
    "result in 'world' " in {
      SimpleWordParser.parse(SimpleWordParser.word, "world").get must be_==("world")
    }
    "be parsed successfully" in {
      SimpleWordParser.parse(SimpleWordParser.word, "world").successful isSuccess
    }
  }}