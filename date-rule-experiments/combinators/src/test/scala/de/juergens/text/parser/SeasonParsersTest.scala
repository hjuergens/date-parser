package de.juergens.text.parser

import java.time.LocalDate

import de.juergens.text.{DateRuleParsers, SeasonParsers}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

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

  @Test
  def testSeasonStart: Unit = {
    val parsers = new DateRuleParsers with SeasonParsers
    val date = LocalDate.of(2025, 6, 9)
    def start(text: String) = parsers.parseAll(parsers.season, text).get(date)

    assertEquals(LocalDate.of(2025, 3, 20), start("spring"))
    assertEquals(LocalDate.of(2025, 6, 21), start("summer"))
    assertEquals(LocalDate.of(2025, 9, 22), start("autumn"))
    assertEquals(LocalDate.of(2025, 9, 22), start("fall"))
    assertEquals(LocalDate.of(2025, 12, 21), start("winter"))
  }
}