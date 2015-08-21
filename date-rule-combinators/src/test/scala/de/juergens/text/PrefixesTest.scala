package de.juergens.text

import java.io.{File, FilenameFilter}

import org.junit.rules.{ExpectedException, ExternalResource}
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.junit.{Rule, Test}

import scala.collection.JavaConversions._
import scala.io.Source

// TODO extends JUnit4TestClass

//@TestNG(dataProvider = "text files", timeOut = 1000)
@RunWith(value = classOf[Parameterized])
class PrefixesTest(textFile:File)  {

  private var source : Source = _

  @Rule
  def thrown = _thrown
  val _thrown : ExpectedException = ExpectedException.none()

  @Rule
  def resource = _resource
  val _resource : ExternalResource = new ExternalResource() {
    @Override
    @throws[Throwable]
    override protected def before() {
      source = Source.fromURI { textFile.toURI }
    }

    @Override
    override def after() {
      source.close()
    }
  }

  @Test(expected=classOf[java.lang.RuntimeException])
  def allLines = {
      println("file: " + textFile)
      for (lineArray <- lines) {
        adjusterTest(lineArray(0).toString)
      }
  }

  //  @DataProviderNG(name = "lines in text file", parallel = true)
  def lines: java.util.Iterator[Array[Object]] = {
    require(source != null)
    source.ensuring(_.nerrors == 0)
    source.getLines().filterNot(_.trim.startsWith("#")).filterNot(_.isEmpty).map(Array[Object](_))
  }

  val parser = new DateRuleParser

  def adjusterTest(line: String) {
    parser.parseAll(parser.adjuster, line).get
  }

}


object PrefixesTest {

  //  def apply(p : java.io.File) = {
  //    val test = new PrefixesTest
  //    test.textFile = p
  //    test
  //  }

  import java.io.File

  @Parameters(name = "{index}: {0}")
  def listTextFiles: java.lang.Iterable[Array[AnyRef]] = {
    val dir = new File(getClass.getResource("/").getFile)
    val files = dir.listFiles(new FilenameFilter {
      def accept(dir: File, name: String): Boolean = name.endsWith("txt") && !name.contains("seek")
    })
    files.map(Array[AnyRef](_)).toSeq
  }

  //  @DataProviderNG(name = "text files", parallel = true)
  def listTextFilesNG: java.util.Iterator[Array[AnyRef]] = {
    val dir = new File(getClass.getResource("/").getFile)
    val files = dir.listFiles(new FilenameFilter {
      def accept(dir: File, name: String): Boolean = name.endsWith("txt")
    })
    files.map(Array[AnyRef](_)).toIterator
  }

  //  @Factory(dataProvider = "text files")
  //  def factoryMethod(): Array[Object] = {
  //    for{
  //      fileArray <- listTextFiles.toArray
  //      file = fileArray(0).asInstanceOf[File]
  //    } yield PrefixesTest(file).asInstanceOf[Object]
  //  }
}

