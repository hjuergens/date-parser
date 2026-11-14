package de.juergens.text

import java.io.File

import de.juergens.text.DateRuleParsers
import org.scalatest.funsuite.AnyFunSuite

import scala.io.Source

/**
  * Test cases from natty.txt to identify parser gaps
  */
class NattyDatesTest extends AnyFunSuite {

  val parser = new DateRuleParsers with DateParsers

  // Get all lines from the natty_dates.txt test file
  val testFile = new File(getClass.getResource("/natty_dates.txt").getFile)
  val lines: Seq[String] = Source.fromFile(testFile).getLines()
    .filterNot(_.trim.isEmpty)
    .filterNot(_.startsWith("#"))
    .map(_.trim)
    .toSeq

  // Test each line to see if it can be parsed
  lines.foreach { line =>
    test(s"Parse: $line") {
      // Try different parsers
      val parsersToTry = Seq("adjuster", "monthDay", "date", "dayOfWeekInMonth", "seekDayOfWeek")
      
      var parsedSuccessfully = false
      var lastError: String = ""
      
      parsersToTry.foreach { parserName =>
        try {
          val parserMethod = parser.getClass.getMethod(parserName)
          val p = parserMethod.invoke(parser).asInstanceOf[parser.Parser[Any]]
          val result = parser.parseAll(p, line.toLowerCase)
          
          if (result.successful) {
            parsedSuccessfully = true
            info(s"Successfully parsed with $parserName: ${result.get}")
          } else {
            lastError = s"$parserName: ${result.toString.take(100)}"
          }
        } catch {
          case e: Exception =>
            lastError = s"$parserName: ${e.getMessage}"
        }
      }
      
      if (!parsedSuccessfully) {
        fail(s"Could not parse '$line' with any parser. Last error: $lastError")
      }
    }
  }
}
