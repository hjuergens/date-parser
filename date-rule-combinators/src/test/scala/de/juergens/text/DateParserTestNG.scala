package de.juergens.text

import java.time.LocalDate

import org.testng.annotations.{AfterMethod, BeforeMethod, Test}

class DateParserTestNG extends DateParser {
  @Test
  @throws(classOf[Exception])
  def testParse() {
    parse(date, "23.07.2015").get equals LocalDate.of(2015,7,23)
  }
}