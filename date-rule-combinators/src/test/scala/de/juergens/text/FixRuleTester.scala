package de.juergens.text

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.{Ignore, Test}

/**
 * Created by juergens on 20.05.15.
 */
@Ignore
@RunWith(classOf[JUnit4]) class FixRuleTester {

  import scala.io.Source

  val lines =
    Source.fromURI {
      getClass.getResource("/fixrule.txt").toURI
    }.getLines()


  @Test def testDateShifter {
    val parser = new SimpleRuleParser
    for(line <- lines.filterNot(_.startsWith("#")))
      parser.parseAll(parser.fixRule, line).get
  }
}