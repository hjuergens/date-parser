package de.juergens.experimental

import java.io.File
import java.nio.file.Paths
import java.util.{Iterator => JIterator}

import de.juergens.FileTesterCompanion._
import org.testng.annotations.{DataProvider, Factory, Parameters, Test}

import scala.collection.JavaConversions._

case class TestLine(file:File, line :String) {


  @Parameters(value=Array{"line"})
  @Test(dataProvider = "single line", timeOut = 1000)
  def testMethod(line: String) {
    System.out.println("The parameter value is: " + line)
  }

  @DataProvider(name = "single line")
  def lines: JIterator[Array[Object]] = Seq(Array(line.asInstanceOf[Object])).iterator

}

object TestFile {

  @DataProvider(name = "line")
  def lines(file: File): Array[Object] = {
    (for {
      line <- linesOfFile("/" + file.getName)
      inst = line.apply(0).toString.asInstanceOf[Object]
    } yield inst).toArray
  }

  @Factory(dataProvider = "dp")
  def createInstances(file: File): Array[Object] = {
    (for {
      line <- linesOfFile("/" + file.getName)
      inst = new TestLine(file, line.apply(0).toString).asInstanceOf[Object]
    } yield inst).toArray
  }

  @DataProvider(name = "dp")
  def dataProvider(): JIterator[Array[Object]] = {
    val url = getClass.getResource("/")
    val root = Paths.get(url.toURI)
    val list = listFiles(new File(url.getFile), """^[a-z].*\.txt""".r)
    list
      .map(f => root.relativize(f.toPath))
      .map(p => Array.apply(p.toFile.asInstanceOf[Object])).iterator
  }
}
