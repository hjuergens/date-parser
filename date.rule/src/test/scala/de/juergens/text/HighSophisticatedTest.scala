package de.juergens.text

/**
 * Created by juergens on 31.05.15.
 */

import java.io.{File, FilenameFilter}
import java.net.URL
import java.{io => jio, lang => jl, util => ju}

import org.junit._
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.testng.annotations.{Parameters => ParametersNG}
import org.testng.annotations.{DataProvider => DataProviderNG, Test => TestNG}
import org.testng.annotations.{DataProvider => DataProviderNG, Test => TestNG, _}
import scala.collection.JavaConversions._
import scala.io.Source

@Ignore
@TestNG
@RunWith(value = classOf[Parameterized])
class HighSophisticatedTest(line: String) {

  val parser = new DateRuleParser

  @Before
  def before {
    println( s"length=${line.length}")
    println("line: " + line)
  }

  @After
  def after { println("line: " + line) }

  @Test(timeout = 1000)
  def test() : Unit =  {
    parser.parseAll(parser.adjuster, line).get
  }

  @DataProviderNG(name = "lines in text file", parallel = true)
  def lines = HighSophisticatedTest.linesNG

  @ParametersNG( Array("line") )
  @TestNG(dataProvider = "lines in text file", timeOut = 1000)
  def test(_line:String) : Unit =  {
    parser.parseAll(parser.adjuster, _line).get
  }
}

// NOTE: Defined AFTER companion class to prevent:
// Class com.openmip.drm.JUnit4ParameterizedTest has no public
// constructor TestCase(String name) or TestCase()
object HighSophisticatedTest {

  val textFile = {
    val url = getClass.getResource("/high_sophisticated.txt")
    new File(url.getFile)
  }

  @org.junit.BeforeClass
  def before {
    println(s"textFile=$textFile")
  }

  @org.junit.AfterClass
  def after {
    println(s"textFile=$textFile")
  }

  // java.util.Iterator
  def _lines : Iterator[Array[Object]] = {
    val filteredLines = Source.fromURI {
      textFile.toURI
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
