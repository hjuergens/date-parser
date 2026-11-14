package de.juergens.text.parsers

import java.time.LocalDate

import de.juergens.text.TermParsers
import org.junit.Assert._
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(value = classOf[JUnit4])
class FinanceTermParsersTest extends TermParsers {

  @Test
  def testStandardPeriod() :Unit = {
    {
      val term3M = parseAll(standardPeriod, "3M")
      assertTrue(term3M.toString, term3M.successful)

      val referenceDate = LocalDate.of(2015,8,20)

      val mnth3 = term3M.get.addTo(referenceDate)

      assertEquals(mnth3, LocalDate.of(2015,11,20))
    }
  }

}