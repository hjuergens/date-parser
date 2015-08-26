package de.juergens.text

import java.time.LocalDate

import org.testng.annotations.{AfterMethod, BeforeMethod, Test}

/**
 * Created by juergens on 31.05.15.
 */
class DateParserTestNG extends DateParser {
  @BeforeMethod
  @throws(classOf[Exception])
  def setUp: Unit = {
  }

  @AfterMethod
  @throws(classOf[Exception])
  def tearDown: Unit = {
  }

  @Test
  @throws(classOf[Exception])
  def testParse {
    parse(date, "23.07.2015").get equals LocalDate.of(2015,7,23)
  }
}