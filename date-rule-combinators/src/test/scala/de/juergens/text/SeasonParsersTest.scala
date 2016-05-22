package de.juergens.text

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert.assertTrue
import org.junit.Test

@RunWith(value = classOf[JUnit4])
class SeasonParsersTest {
  @Test
  @throws[Exception]
  def testSeason: Unit = {
    val parsers = new DateRuleParsers with SeasonParsers

    assertTrue( parsers.parseAll(parsers.season, "spring").successful)
    assertTrue( parsers.parseAll(parsers.season, "summer").successful)
    assertTrue( parsers.parseAll(parsers.season, "autumn").successful)
    assertTrue( parsers.parseAll(parsers.season, "fall").successful)
    assertTrue( parsers.parseAll(parsers.season, "winter").successful)
  }
}