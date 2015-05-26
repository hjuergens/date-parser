package de.juergens

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Created by juergens on 20.05.15.
 */
@RunWith(classOf[JUnit4]) class RelativeDatesTest {

  import scala.io.Source

  val lines =
    Source.fromURI {
      getClass.getResource("/relative_dates.txt").toURI
    }.getLines()


  @Test def testDateShifter {
    val parser = new RuleParser
    parser.parseAll(parser.timeUnit, "day").get
  }
}