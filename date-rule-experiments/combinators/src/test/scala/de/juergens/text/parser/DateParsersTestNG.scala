package de.juergens.text.parser

import java.time.LocalDate

import de.juergens.text.DateParsers
import org.testng.annotations.Test

class DateParsersTestNG extends DateParsers {
  @Test
  @throws(classOf[Exception])
  def testParse() {
    parse(date, "23.07.2015").get equals LocalDate.of(2015,7,23)
  }
}