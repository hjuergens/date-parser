package de.juergens.text

import java.io.{File, FilenameFilter}

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.{Parameter, Parameters}
import org.testng.annotations.{DataProvider => DataProviderNG, Test => TestNG, _}

import scala.collection.JavaConversions._

// TODO extends JUnit4TestClass

@TestNG(dataProvider = "text files", timeOut = 1000)
@RunWith(value = classOf[Parameterized])
class RelativeDatesTestNG()  {

  @Parameter(value = 0)
  var _textFile: java.io.File=new File(getClass.getResource("/prefixes.txt").getFile)

  // Getter
  def textFile = _textFile

  // Setter
  def textFile_= (value:java.io.File):Unit = _textFile = value

  def textFile(value:java.io.File):Unit = _textFile = value

//  def this() = this(new File(getClass.getResource("/seek.txt").getFile))

  import scala.io.Source
  @Test(expected=classOf[IndexOutOfBoundsException])
  def pushTest = {
    println("file: " + textFile)
    for( lineArray <- lines) {
      test(lineArray(0).toString)
    }
  }

  @DataProviderNG(name = "lines in text file", parallel = true)
  def lines: java.util.Iterator[Array[Object]] =
    Source.fromURI {
      textFile.toURI
    }.getLines().filterNot(_.trim.startsWith("#")).filterNot(_.isEmpty).map(Array[Object](_))


  //@Parameters("lines in text file")
  @TestNG(dataProvider = "lines in text file", timeOut = 1000)
  def test(line: String) {
    val parser = new DateRuleParser
    parser.parseAll(parser.seekWeekDay, line).get
  }

//  @DataProviderNG(name = "text files", parallel = true)
//  def listTextFiles = RelativeDatesTestNG.listTextFiles
}


object RelativeDatesTestNG {

  def apply(p : java.io.File) = {
    val test = new RelativeDatesTestNG
    test.textFile = p
    test
  }

  import java.io.File

  @Parameters(name = "{index}: {0}")
  def listTextFiles: java.lang.Iterable[Array[AnyRef]] = {
    val dir = new File(getClass.getResource("/").getFile)
    val files = (dir.listFiles(new FilenameFilter {
      def accept(dir: File, name: String): Boolean = name.endsWith("txt")
    }))
    files.map(Array[AnyRef](_)).toSeq
  }

  @DataProviderNG(name = "text files", parallel = true)
  def listTextFilesNG: java.util.Iterator[Array[AnyRef]] = {
    val dir = new File(getClass.getResource("/").getFile)
    val files = (dir.listFiles(new FilenameFilter {
      def accept(dir: File, name: String): Boolean = name.endsWith("txt")
    }))
    files.map(Array[AnyRef](_)).toIterator
  }

  @Factory(dataProvider = "text files")
  def factoryMethod(): Array[Object] = {
    for{
      fileArray <- listTextFiles.toArray
      file = fileArray(0).asInstanceOf[File]
    } yield RelativeDatesTestNG(file).asInstanceOf[Object]
  }
}

