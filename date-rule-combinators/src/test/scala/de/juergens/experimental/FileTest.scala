package de.juergens.experimental

import java.io.{File, InputStream}
import java.nio.file.{DirectoryStream, Files, Path, Paths}

import de.juergens.experimental.FileTest._
import org.testng.AssertJUnit._
import org.testng.Reporter
import org.testng.annotations._

import scala.collection.JavaConversions._
import scala.io.Source

class FileTest2  {
  var file : File = _

  @Factory(dataProvider = "files")
  def this(n:File) = {
    this()
    file = n
    Reporter.log(s"file=$file",0,true)
  }

  @DataProvider(name = "lines")
  def lines : Array[Array[Object]] = FileTest.lines(file)

  @Test(dataProvider="lines", enabled = false)
  def test(line:String) : Unit = {
    Reporter.log(file.toString)
    assertNotNull(s"line=$line", line)
  }
}

object FileTest2 {
  @DataProvider(name="files")
  def dp : Array[Array[Object]] = {
    for {
      i <- Array("/_prefixes.txt", "/adjuster.txt")
    } yield Array[Object](i.asInstanceOf[Object])
    FileTest.files
  }
}


@Listeners(value=Array(classOf[org.testng.reporters.JUnitXMLReporter]))
class FileTest {

  @Parameters(value=Array("file", "line"))
  @Test(dataProvider="files-lines")
  def test(file: File, line:String) : Unit = {
    Reporter.log(line, 99, logToStandardOut)
    assertNotNull(s"line=$line", line)
  }
}

object FileTest {
  val logToStandardOut=true

  @DataProvider(name = "files")
  def files : Array[Array[Object]] = {
    Reporter.log("files", 0, logToStandardOut)

    val uri = getClass.getResource("/").toURI
    val dir = Paths.get(uri)
    val builder = Array.newBuilder[Path]
    val filter = new DirectoryStream.Filter[Path] {
      override def accept(entry: Path): Boolean =
        entry.toString.endsWith("txt")
    }
    for (path <- Files.newDirectoryStream(dir, filter))
      builder += path

    for {
      path <- builder.result()
      relPath = dir.relativize(path)
    } yield Array[Object]( relPath.toFile )
  }

  @DataProvider(name = "lines")
  def lines(file:File) : Array[Array[Object]] = {
    Reporter.log("dp lines")
    val resourceName = "/" + file.toString
    val stream = getClass.getResourceAsStream(resourceName)
    for {
      line <- linesFromStream(stream).toArray
    } yield Array[Object]( line )
  }

  private def linesFromStream(inputStream:InputStream) : Iterator[Object] = {
    implicit val co = scala.io.Codec.UTF8
    val filteredLines = Source.fromInputStream{
      inputStream
    }.getLines().map(_.trim)
      .filterNot(_.startsWith("#"))
      .filterNot(_.isEmpty)
      .filterNot(_.forall(_.isWhitespace))
      .filterNot(_.forall(_ == '\u200B'))
    filteredLines
  }

  @DataProvider(name = "files-lines")
  def filesLines : Array[Array[Object]] = {
    implicit val co = scala.io.Codec.UTF8
    for{
      file <- files.map(_.apply(0))
      resourceName = "/" + file.toString
      line <- linesFromStream(getClass.getResourceAsStream(resourceName))
    } yield Array[Object]( file, line )
  }
}
