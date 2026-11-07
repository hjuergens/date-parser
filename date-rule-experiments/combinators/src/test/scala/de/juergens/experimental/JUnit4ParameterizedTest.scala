package de.juergens.experimental

import de.juergens.text.DateRuleParsers
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.testng.Reporter

import java.io.{File, FilenameFilter}
import java.time.temporal.Temporal
import java.{io => jio}

@RunWith(value = classOf[Parameterized])
class JUnit4ParameterizedTest(textFile: jio.File) {
  @Test
  def pushTest :Unit = {
    Reporter.log("file: " + textFile)
    for( lineArray <- lines) {
      test(lineArray(0).toString)
    }

  }

//  @Parameter

  import scala.io.Source

  def lines: Iterator[Array[Object]] =
    Source.fromURI {
      textFile.toURI
    }.getLines().filterNot(_.trim.startsWith("#")).filterNot(_.isEmpty).map(Array[Object](_))


  def test(line: String) {
    val parser = new DateRuleParsers {
      def rule : Parser[Temporal => Stream[Temporal]] = """.*""".r ^^
        { _ => (t:Temporal) => Stream.empty[Temporal]}
    }
    parser.parseAll(parser.rule, line).get
  }

}





// NOTE: Defined AFTER companion class to prevent:
// Class com.openmip.drm.JUnit4ParameterizedTest has no public
// constructor TestCase(String name) or TestCase()
object JUnit4ParameterizedTest {

  // NOTE: Must return collection of Array[AnyRef] (NOT Array[Any]).
//  @Parameters
//  def parameters: ju.Collection[Array[jio.File]] = {
//    val list = new ju.ArrayList[Array[jio.File]]()
//    (1 to 10).foreach(n => list.add(Array(n)))
//    list
//  }
  import scala.jdk.CollectionConverters._

  @Parameters(name = "{index}: {0}")
  def listTextFiles: java.lang.Iterable[Array[Object]] = {
    val dir = new File(getClass.getResource("/").getFile)
    val files = (dir.listFiles(new FilenameFilter {
      def accept(dir: File, name: String): Boolean = name.endsWith("txt")
    }))
    files.map(Array[AnyRef](_)).toIterable.asJava
  }

}

