package de.juergens.text.parsers

/**
  * Created by juergens on 03.07.16.
  */
import java.io.File
import java.time.temporal.TemporalQuery
import java.time.{LocalDate, Year}

import de.juergens.FileTesterCompanion
import de.juergens.text.{DateParsers, ParserTest, ParserTestCompanion}
import org.junit._
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.testng.Reporter

import scala.collection.JavaConversions._



@RunWith(value = classOf[Parameterized])
class MonthdayTest(line: String) extends ParserTest[TemporalQuery[LocalDate]](new DateParsers {}) {

  @Before
  def before() {
    Reporter.log( s"length=${line.length}")
    Reporter.log("line: " + line)
  }

  @After
  def after() { Reporter.log("line: " + line) }

  @Test(timeout = 1500)
  def test() : Unit =  { test(line) }

  private[text] def test(_line:String) : Unit =  {
    val result = parse("monthDay", _line.toLowerCase).get
    val localDate = result.queryFrom(Year.of(2016))
  }
}

object MonthdayTest extends ParserTestCompanion {

  val textFile = new File("/parsers/monthday.txt")

  @org.junit.BeforeClass
  def before() {
    Reporter.log(s"textFile=$textFile")
  }

  @org.junit.AfterClass
  def after() {
    Reporter.log(s"textFile=$textFile")
  }

  private[text] def _lines : Iterator[Array[Object]] =
    FileTesterCompanion.linesOfFile("/parsers/monthday.txt").iterator()

  // NOTE: Must return collection of Array[AnyRef] (NOT Array[Any]).
  @Parameters(name = "{index}: {0}")
  def linesJUnit: java.lang.Iterable[Array[Object]] = _lines.toSeq

}
