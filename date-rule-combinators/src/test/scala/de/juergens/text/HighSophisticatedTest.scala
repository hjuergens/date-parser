package de.juergens.text

/**
 * Created by juergens on 31.05.15.
 */

import java.{io => jio, lang => jl, util => ju}

import de.juergens.time.LocalDateAdjuster
import org.hamcrest.Description
import org.junit.Assert._
import org.junit._
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.testng.Reporter
import org.testng.annotations.{DataProvider => DataProviderNG, Parameters => ParametersNG, Test => TestNG}

import scala.collection.JavaConversions._
import scala.io.Source

@TestNG
@RunWith(value = classOf[Parameterized])
class HighSophisticatedTest(line: String) {

  val parser = new DateRuleParsers

  @Before
  def before() {
    Reporter.log( s"length=${line.length}")
    Reporter.log("line: " + line)
  }

  @After
  def after() { Reporter.log("line: " + line) }

  @Test(timeout = 1000)
  def test() : Unit =  {
    test(line)
  }

  @DataProviderNG(name = "lines in text file", parallel = true)
  def lines = HighSophisticatedTest.linesNG

  @ParametersNG( Array("line") )
  @TestNG(dataProvider = "lines in text file", timeOut = 1000)
  def test(_line:String) : Unit =  {
    val parsResult = parser.parseAll(parser.adjuster, _line.toLowerCase)
    assertTrue(parsResult.toString, parsResult.successful)
    assertThat(parsResult, matcher)
  }

  object matcher extends org.hamcrest.BaseMatcher[parser.ParseResult[LocalDateAdjuster]] {
    override def matches(o: scala.Any): Boolean = o match {
      case result : parser.ParseResult[_] => true
      case _ => false
    }

    override def describeMismatch(o: scala.Any, description: Description): Unit = ???

    override def describeTo(description: Description): Unit = ???
  }

}

// NOTE: Defined AFTER companion class to prevent:
// Class com.openmip.drm.JUnit4ParameterizedTest has no public
// constructor TestCase(String name) or TestCase()
object HighSophisticatedTest {

  val inputStream = {
    getClass.getResourceAsStream("/high_sophisticated.txt")
  }

  @org.junit.BeforeClass
  def before() {  }

  @org.junit.AfterClass
  def after() {  }

  // java.util.Iterator
  def _lines : Iterator[Array[Object]] = {
    val filteredLines = Source.fromInputStream{
      inputStream
    }.getLines().map(_.trim)
      .filterNot(_.startsWith("#"))
      .filterNot(_.isEmpty)
      .filterNot(_.forall(_.isWhitespace))
      .filterNot(_.forall(_ == '\u200B'))
    filteredLines.map(Array[Object](_))
  }

  // NOTE: Must return collection of Array[AnyRef] (NOT Array[Any]).
  @Parameters(name = "{index}: {0}")
  def linesJUnit: java.lang.Iterable[Array[Object]] = _lines.toSeq

  val linesNG: java.util.Iterator[Array[Object]] = _lines

//  @Factory
//  def factoryMethod(): Array[Object] = {
//    for{
//      fileArray <- linesNG.toArray
//      line = fileArray(0).asInstanceOf[String]
//    } yield new HighSophisticatedTest(line).asInstanceOf[Object]
//  }

}
